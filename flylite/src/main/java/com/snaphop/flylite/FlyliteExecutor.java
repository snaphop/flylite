/*
 * Copyright (C) Red Gate Software Ltd 2010-2023
 * Modifications Copyright (C) Snaphop 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.snaphop.flylite;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.snaphop.flylite.api.ClassProvider;
import com.snaphop.flylite.api.ErrorCode;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.ResourceProvider;
import com.snaphop.flylite.api.callback.Callback;
import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.api.migration.JavaMigration;
import com.snaphop.flylite.internal.callback.CallbackExecutor;
import com.snaphop.flylite.internal.callback.DefaultCallbackExecutor;
import com.snaphop.flylite.internal.callback.SqlScriptCallbackFactory;
import com.snaphop.flylite.internal.database.DatabaseType;
import com.snaphop.flylite.internal.database.base.Database;
import com.snaphop.flylite.internal.database.base.Schema;
import com.snaphop.flylite.internal.jdbc.JdbcConnectionFactory;
import com.snaphop.flylite.internal.logging.Log;
import com.snaphop.flylite.internal.parser.ParsingContext;
import com.snaphop.flylite.internal.resolver.CompositeMigrationResolver;
import com.snaphop.flylite.internal.resource.NoopResourceProvider;
import com.snaphop.flylite.internal.resource.ResourceNameValidator;
import com.snaphop.flylite.internal.resource.StringResource;
import com.snaphop.flylite.internal.scanner.LocationScannerCache;
import com.snaphop.flylite.internal.scanner.ResourceNameCache;
import com.snaphop.flylite.internal.scanner.Scanner;
import com.snaphop.flylite.internal.schemahistory.SchemaHistory;
import com.snaphop.flylite.internal.schemahistory.SchemaHistoryFactory;
import com.snaphop.flylite.internal.sqlscript.SqlScript;
import com.snaphop.flylite.internal.sqlscript.SqlScriptExecutorFactory;
import com.snaphop.flylite.internal.sqlscript.SqlScriptFactory;
import com.snaphop.flylite.internal.strategy.RetryStrategy;
import com.snaphop.flylite.internal.util.IOUtils;

/**
 * Sets up everything a command needs (connections, resolvers, schema history, callbacks),
 * runs it and tears it down again.
 */
final class FlyliteExecutor {

	private static final Log LOG = Log.of(FlyliteExecutor.class);

	/**
	 * Everything a command needs to run.
	 */
	record Context(CompositeMigrationResolver migrationResolver, SchemaHistory schemaHistory, Database<?> database,
			Schema<?, ?> defaultSchema, List<Schema<?, ?>> schemas, CallbackExecutor callbackExecutor) {
	}

	interface Command<T> {

		T execute(Context context);

	}

	/**
	 * Used to cache resource names for classpath scanning between commands.
	 */
	private final ResourceNameCache resourceNameCache = new ResourceNameCache();

	/**
	 * Used to cache LocationScanners between commands.
	 */
	private final LocationScannerCache locationScannerCache = new LocationScannerCache();

	private final ResourceNameValidator resourceNameValidator = new ResourceNameValidator();

	private final Configuration configuration;

	/**
	 * Whether the database connection info has already been printed in the logs.
	 */
	private boolean dbConnectionInfoPrinted;

	FlyliteExecutor(Configuration configuration) {
		this.configuration = configuration;
	}

	/**
	 * Executes this command with proper resource handling and cleanup.
	 * @param command the command to execute.
	 * @param scannerRequired whether locations need to be scanned even if default
	 * resolvers and callbacks are skipped.
	 * @param <T> the type of the result.
	 * @return the result of the command.
	 */
	<T> T execute(Command<T> command, boolean scannerRequired) {
		DataSource dataSource = validateConfiguration(configuration);

		final ResourceProvider resourceProvider;
		final ClassProvider<JavaMigration> classProvider;
		if (!scannerRequired && configuration.isSkipDefaultResolvers() && configuration.isSkipDefaultCallbacks()) {
			resourceProvider = NoopResourceProvider.INSTANCE;
			classProvider = List::of;
		}
		else {
			var customResources = configuration.getResourceProvider();
			var customClasses = configuration.getJavaMigrationClassProvider();
			if (customResources != null && customClasses != null) {
				resourceProvider = customResources;
				classProvider = customClasses;
			}
			else {
				Scanner<JavaMigration> scanner = new Scanner<>(JavaMigration.class, configuration.getLocations(),
						configuration.getClassLoader(), configuration.getEncoding(), resourceNameCache,
						locationScannerCache, configuration.isFailOnMissingLocations());
				resourceProvider = customResources != null ? customResources : scanner;
				classProvider = customClasses != null ? customClasses : scanner;
			}
		}

		final ParsingContext parsingContext = new ParsingContext();

		resourceNameValidator.validateSQLMigrationNaming(resourceProvider, configuration);

		JdbcConnectionFactory jdbcConnectionFactory = new JdbcConnectionFactory(dataSource, configuration);

		final DatabaseType databaseType = jdbcConnectionFactory.getDatabaseType();
		final SqlScriptFactory sqlScriptFactory = databaseType.createSqlScriptFactory(configuration, parsingContext);
		RetryStrategy.setNumberOfRetries(configuration.getLockRetryCount());

		final SqlScriptExecutorFactory sqlScriptExecutorFactory = databaseType.createSqlScriptExecutorFactory();

		final String initSql = configuration.getInitSql();
		if (initSql != null) {
			jdbcConnectionFactory.setConnectionInitializer((factory, connection) -> {
				SqlScript sqlScript = sqlScriptFactory.createSqlScript(new StringResource(initSql), true,
						resourceProvider);
				sqlScriptExecutorFactory.createSqlScriptExecutor(connection).execute(sqlScript);
			});
		}

		Database<?> database = null;
		try {
			database = databaseType.createDatabase(configuration, !dbConnectionInfoPrinted, jdbcConnectionFactory);
			dbConnectionInfoPrinted = true;
			LOG.debug("DDL Transactions Supported: " + database.supportsDdlTransactions());

			SchemaHistoryFactory.Schemas schemas = SchemaHistoryFactory.prepareSchemas(configuration, database);
			Schema<?, ?> defaultSchema = schemas.defaultSchema();

			parsingContext.populate(database, configuration);

			database.ensureSupported();

			DefaultCallbackExecutor callbackExecutor = new DefaultCallbackExecutor(configuration, database,
					defaultSchema, prepareCallbacks(resourceProvider, sqlScriptExecutorFactory, sqlScriptFactory));

			SchemaHistory schemaHistory = SchemaHistoryFactory.getSchemaHistory(configuration, sqlScriptExecutorFactory,
					sqlScriptFactory, database, defaultSchema);

			CompositeMigrationResolver resolver = new CompositeMigrationResolver(resourceProvider, classProvider,
					configuration, sqlScriptExecutorFactory, sqlScriptFactory, parsingContext);

			return command.execute(
					new Context(resolver, schemaHistory, database, defaultSchema, schemas.all(), callbackExecutor));
		}
		finally {
			IOUtils.close(database);
			IOUtils.close(jdbcConnectionFactory);
		}
	}

	private static DataSource validateConfiguration(Configuration configuration) {
		DataSource dataSource = configuration.getDataSource();
		if (dataSource == null) {
			throw new FlyliteException("Unable to connect to the database. Configure the url, user and password!",
					ErrorCode.CONFIGURATION);
		}
		for (String key : configuration.getPlaceholders().keySet()) {
			if (key.toLowerCase(java.util.Locale.ENGLISH).startsWith("flyway:")) {
				throw new FlyliteException("Invalid placeholder ('flyway:' prefix is reserved): " + key);
			}
		}
		return dataSource;
	}

	private List<Callback> prepareCallbacks(ResourceProvider resourceProvider,
			SqlScriptExecutorFactory sqlScriptExecutorFactory, SqlScriptFactory sqlScriptFactory) {
		List<Callback> effectiveCallbacks = new ArrayList<>(configuration.getCallbacks());
		if (!configuration.isSkipDefaultCallbacks()) {
			effectiveCallbacks.addAll(new SqlScriptCallbackFactory(resourceProvider, sqlScriptExecutorFactory,
					sqlScriptFactory, configuration)
				.getCallbacks());
		}
		return effectiveCallbacks;
	}

}
