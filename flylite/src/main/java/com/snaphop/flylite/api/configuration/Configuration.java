/*
 * Copyright (C) Red Gate Software Ltd 2010-2023
 * Modifications Copyright (C) Snaphop 2026
 * Copyright (C) Snaphop 2026
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
package com.snaphop.flylite.api.configuration;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.api.ClassProvider;
import com.snaphop.flylite.api.Location;
import com.snaphop.flylite.api.MigrationVersion;
import com.snaphop.flylite.api.ResourceProvider;
import com.snaphop.flylite.api.callback.Callback;
import com.snaphop.flylite.api.migration.JavaMigration;
import com.snaphop.flylite.api.pattern.ValidatePattern;
import com.snaphop.flylite.api.resolver.MigrationResolver;

/**
 * Read-only Flylite configuration. Use {@link FluentConfiguration} to create one.
 */
public interface Configuration {

	/**
	 * The default schema history table name. It is the same as Flyway's so that existing
	 * databases migrated with Flyway can be migrated with Flylite.
	 */
	String DEFAULT_TABLE = "flyway_schema_history";

	/**
	 * Class loader used to load migrations, resources and JDBC drivers.
	 * @return class loader.
	 */
	ClassLoader getClassLoader();

	/**
	 * The JDBC URL if the configuration was created with one.
	 * @return url or {@code null} if a datasource was given directly.
	 */
	@Nullable String getUrl();

	/**
	 * The database user if the configuration was created with a JDBC URL.
	 * @return user or {@code null}.
	 */
	@Nullable String getUser();

	/**
	 * The database password if the configuration was created with a JDBC URL.
	 * @return password or {@code null}.
	 */
	@Nullable String getPassword();

	/**
	 * The fully qualified class name of the JDBC driver. If {@code null} the driver is
	 * detected from the URL.
	 * @return driver class name or {@code null}.
	 */
	@Nullable String getDriver();

	/**
	 * The datasource to migrate.
	 * @return datasource or {@code null} if neither a datasource nor a URL was
	 * configured.
	 */
	@Nullable DataSource getDataSource();

	/**
	 * Extra JDBC properties passed to the driver when a URL is used.
	 * @return jdbc properties never {@code null}.
	 */
	Map<String, String> getJdbcProperties();

	/**
	 * The maximum number of retries when attempting to connect to the database. Each
	 * retry waits twice as long as the previous one up to
	 * {@link #getConnectRetriesInterval()}.
	 * @return connect retries, {@code 0} by default.
	 */
	int getConnectRetries();

	/**
	 * Maximum time between connection retries in seconds.
	 * @return interval in seconds, {@code 120} by default.
	 */
	int getConnectRetriesInterval();

	/**
	 * SQL statements to run to initialize a new database connection immediately after
	 * opening it.
	 * @return init sql or {@code null}.
	 */
	@Nullable String getInitSql();

	/**
	 * The version to tag an existing schema with when executing baseline.
	 * @return baseline version, {@code 1} by default.
	 */
	MigrationVersion getBaselineVersion();

	/**
	 * The description to tag an existing schema with when executing baseline.
	 * @return baseline description.
	 */
	String getBaselineDescription();

	/**
	 * Custom migration resolvers used in addition to the built-in ones.
	 * @return resolvers.
	 */
	List<MigrationResolver> getResolvers();

	/**
	 * Whether the built-in resolvers (SQL and Java) should be skipped.
	 * @return {@code false} by default.
	 */
	boolean isSkipDefaultResolvers();

	/**
	 * Callbacks for lifecycle notifications.
	 * @return callbacks.
	 */
	List<Callback> getCallbacks();

	/**
	 * Whether the default SQL callbacks (for example {@code beforeMigrate.sql}) should be
	 * skipped.
	 * @return {@code false} by default.
	 */
	boolean isSkipDefaultCallbacks();

	/**
	 * The file name prefix for versioned SQL migrations.
	 * @return {@code V} by default.
	 */
	String getSqlMigrationPrefix();

	/**
	 * The file name prefix for repeatable SQL migrations.
	 * @return {@code R} by default.
	 */
	String getRepeatableSqlMigrationPrefix();

	/**
	 * The file name separator for SQL migrations.
	 * @return {@code __} by default.
	 */
	String getSqlMigrationSeparator();

	/**
	 * The file name suffixes for SQL migrations.
	 * @return {@code .sql} by default.
	 */
	List<String> getSqlMigrationSuffixes();

	/**
	 * Java migration instances used in addition to scanned ones.
	 * @return java migrations.
	 */
	List<JavaMigration> getJavaMigrations();

	/**
	 * Whether SQL should be executed within a transaction.
	 * @return {@code true} by default.
	 */
	boolean isExecuteInTransaction();

	/**
	 * Whether placeholders should be replaced.
	 * @return {@code true} by default.
	 */
	boolean isPlaceholderReplacement();

	/**
	 * The suffix of every placeholder.
	 * @return <code>}</code> by default.
	 */
	String getPlaceholderSuffix();

	/**
	 * The prefix of every placeholder.
	 * @return <code>${</code> by default.
	 */
	String getPlaceholderPrefix();

	/**
	 * The separator of default placeholders.
	 * @return {@code :} by default.
	 */
	String getPlaceholderSeparator();

	/**
	 * The suffix of every script placeholder.
	 * @return {@code __} by default.
	 */
	String getScriptPlaceholderSuffix();

	/**
	 * The prefix of every script placeholder.
	 * @return {@code FP__} by default.
	 */
	String getScriptPlaceholderPrefix();

	/**
	 * The placeholders to replace in SQL migration scripts.
	 * @return placeholders.
	 */
	Map<String, String> getPlaceholders();

	/**
	 * The target version up to which migrations should be considered.
	 * @return {@link MigrationVersion#LATEST} by default.
	 */
	MigrationVersion getTarget();

	/**
	 * The name of the schema history table.
	 * @return {@value #DEFAULT_TABLE} by default.
	 */
	String getTable();

	/**
	 * The tablespace where to create the schema history table. Only used by PostgreSQL.
	 * @return tablespace or {@code null} for the database default.
	 */
	@Nullable String getTablespace();

	/**
	 * The default schema managed by Flylite. If {@code null} the first of
	 * {@link #getSchemas()} is used and if that is empty the connection's current schema.
	 * @return default schema or {@code null}.
	 */
	@Nullable String getDefaultSchema();

	/**
	 * The schemas managed by Flylite.
	 * @return schemas, empty by default.
	 */
	List<String> getSchemas();

	/**
	 * The encoding of SQL migrations.
	 * @return UTF-8 by default.
	 */
	Charset getEncoding();

	/**
	 * Locations to scan recursively for migrations.
	 * @return {@code classpath:db/migration} by default.
	 */
	List<Location> getLocations();

	/**
	 * Whether to automatically call baseline when migrate is executed against a non-empty
	 * schema with no schema history table.
	 * @return {@code false} by default.
	 */
	boolean isBaselineOnMigrate();

	/**
	 * Allows migrations to be run "out of order".
	 * @return {@code false} by default.
	 */
	boolean isOutOfOrder();

	/**
	 * Patterns of migration states to ignore when validating.
	 * @return {@code *:future} by default.
	 */
	List<ValidatePattern> getIgnoreMigrationPatterns();

	/**
	 * Whether to fail if a location contains a file that does not follow the migration
	 * naming convention.
	 * @return {@code false} by default.
	 */
	boolean isValidateMigrationNaming();

	/**
	 * Whether to automatically call validate when running migrate.
	 * @return {@code true} by default.
	 */
	boolean isValidateOnMigrate();

	/**
	 * Whether clean is disabled.
	 * @return {@code true} by default.
	 */
	boolean isCleanDisabled();

	/**
	 * Whether to allow mixing transactional and non-transactional statements within the
	 * same migration.
	 * @return {@code false} by default.
	 */
	boolean isMixed();

	/**
	 * Whether to group all pending migrations together in the same transaction.
	 * @return {@code false} by default.
	 */
	boolean isGroup();

	/**
	 * The username recorded in the schema history table.
	 * @return installed by or {@code null} for the current database user.
	 */
	@Nullable String getInstalledBy();

	/**
	 * Custom resource provider used instead of scanning locations.
	 * @return resource provider or {@code null}.
	 */
	@Nullable ResourceProvider getResourceProvider();

	/**
	 * Custom java migration class provider used instead of scanning locations.
	 * @return class provider or {@code null}.
	 */
	@Nullable ClassProvider<JavaMigration> getJavaMigrationClassProvider();

	/**
	 * Whether Flylite should attempt to create the schemas in {@link #getSchemas()}.
	 * @return {@code true} by default.
	 */
	boolean isCreateSchemas();

	/**
	 * The maximum number of retries when trying to obtain a lock. {@code -1} retries
	 * indefinitely.
	 * @return {@code 50} by default.
	 */
	int getLockRetryCount();

	/**
	 * Whether to fail if a location does not exist.
	 * @return {@code false} by default.
	 */
	boolean isFailOnMissingLocations();

	/**
	 * Whether PostgreSQL uses a transaction scoped advisory lock (instead of a session
	 * lock) while migrating.
	 * @return {@code true} by default.
	 */
	boolean isPostgresqlTransactionalLock();

}
