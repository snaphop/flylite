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

import com.snaphop.flylite.api.ErrorCode;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.MigrationInfoService;
import com.snaphop.flylite.api.callback.Event;
import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.api.configuration.FluentConfiguration;
import com.snaphop.flylite.api.exception.FlyliteValidateException;
import com.snaphop.flylite.api.output.BaselineResult;
import com.snaphop.flylite.api.output.CleanResult;
import com.snaphop.flylite.api.output.MigrateResult;
import com.snaphop.flylite.api.output.RepairResult;
import com.snaphop.flylite.api.output.ValidateResult;
import com.snaphop.flylite.api.pattern.ValidatePattern;
import com.snaphop.flylite.internal.command.DbBaseline;
import com.snaphop.flylite.internal.command.DbInfo;
import com.snaphop.flylite.internal.command.DbMigrate;
import com.snaphop.flylite.internal.command.DbRepair;
import com.snaphop.flylite.internal.command.DbSchemas;
import com.snaphop.flylite.internal.command.DbValidate;
import com.snaphop.flylite.internal.command.clean.DbClean;
import com.snaphop.flylite.internal.database.base.Schema;
import com.snaphop.flylite.internal.logging.Log;
import com.snaphop.flylite.internal.util.StringUtils;

/**
 * This is the centre point of Flylite, and for most users, the only class they will ever
 * have to deal with.
 * <p>
 * It is THE public API from which all important Flylite functions such as clean, validate
 * and migrate can be called.
 * <p>
 * To get started call {@link #configure()}:
 *
 * {@snippet :
 * Flylite flylite = Flylite.configure().dataSource(dataSource).locations("classpath:db/migration").load();
 * flylite.migrate();
 * }
 */
public final class Flylite {

	private static final Log LOG = Log.of(Flylite.class);

	private final Configuration configuration;

	private final FlyliteExecutor executor;

	/**
	 * Creates a new configuration with default settings. Call
	 * {@link FluentConfiguration#load()} to create a Flylite instance.
	 * @return configuration builder.
	 */
	public static FluentConfiguration configure() {
		return new FluentConfiguration();
	}

	/**
	 * Creates a new configuration with default settings using the given class loader.
	 * @param classLoader class loader for migrations, resources and drivers.
	 * @return configuration builder.
	 */
	public static FluentConfiguration configure(ClassLoader classLoader) {
		return new FluentConfiguration(classLoader);
	}

	/**
	 * Creates a new instance of Flylite with this configuration. The configuration is
	 * copied so later changes to it have no effect.
	 * @param configuration the configuration to use.
	 */
	public Flylite(Configuration configuration) {
		this.configuration = new FluentConfiguration(configuration.getClassLoader()).configuration(configuration);
		this.executor = new FlyliteExecutor(this.configuration);
	}

	/**
	 * The configuration of this instance.
	 * @return configuration.
	 */
	public Configuration getConfiguration() {
		return configuration;
	}

	/**
	 * Starts the database migration. All pending migrations will be applied in order.
	 * Calling migrate on an up-to-date database has no effect.
	 * @return an object summarising the successfully applied migrations.
	 * @throws FlyliteException when the migration failed.
	 */
	public MigrateResult migrate() throws FlyliteException {
		return executor.execute(ctx -> {
			if (configuration.isValidateOnMigrate()) {
				List<ValidatePattern> ignorePatterns = new ArrayList<>(configuration.getIgnoreMigrationPatterns());
				ignorePatterns.add(ValidatePattern.fromPattern("*:pending"));
				ValidateResult validateResult = doValidate(ctx, ignorePatterns);
				if (!validateResult.validationSuccessful) {
					throw new FlyliteValidateException(validateResult.errorDetails,
							validateResult.getAllErrorMessages());
				}
			}

			if (configuration.isCreateSchemas()) {
				new DbSchemas(ctx.database(), ctx.schemas(), ctx.schemaHistory(), ctx.callbackExecutor()).create(false);
			}
			else if (!ctx.defaultSchema().exists()) {
				LOG.warn("The configuration option 'createSchemas' is false.\n"
						+ "However, the schema history table still needs a schema to reside in.\n"
						+ "You must manually create a schema for the schema history table to reside in.");
			}

			if (!ctx.schemaHistory().exists()) {
				List<Schema<?, ?>> nonEmptySchemas = new ArrayList<>();
				for (Schema<?, ?> schema : ctx.schemas()) {
					if (schema.exists() && !schema.empty()) {
						nonEmptySchemas.add(schema);
					}
				}

				if (!nonEmptySchemas.isEmpty()) {
					if (configuration.isBaselineOnMigrate()) {
						doBaseline(ctx);
					}
					else if (!ctx.schemaHistory().exists()) {
						// Checked again because a concurrent migrate may have created the
						// schema history table and its first objects since the check
						// above.
						throw new FlyliteException(
								"Found non-empty schema(s) "
										+ StringUtils.collectionToCommaDelimitedString(nonEmptySchemas)
										+ " but no schema history table. Use baseline()"
										+ " or set baselineOnMigrate to true to initialize the schema history table.",
								ErrorCode.NON_EMPTY_SCHEMA_WITHOUT_SCHEMA_HISTORY_TABLE);
					}
				}

				ctx.schemaHistory().create(false);
			}

			MigrateResult result = new DbMigrate(ctx.database(), ctx.schemaHistory(), ctx.defaultSchema(),
					ctx.migrationResolver(), configuration, ctx.callbackExecutor())
				.migrate();

			ctx.callbackExecutor().onOperationFinishEvent(Event.AFTER_MIGRATE_OPERATION_FINISH, result);

			return result;
		}, true);
	}

	/**
	 * Retrieves the complete information about all the migrations including applied,
	 * pending and current migrations with details and status.
	 * @return all migrations with details and status.
	 * @throws FlyliteException when the info retrieval failed.
	 */
	public MigrationInfoService info() {
		return executor.execute(ctx -> {
			MigrationInfoService migrationInfoService = new DbInfo(ctx.migrationResolver(), ctx.schemaHistory(),
					configuration, ctx.database(), ctx.callbackExecutor(), ctx.schemas())
				.info();
			ctx.callbackExecutor()
				.onOperationFinishEvent(Event.AFTER_INFO_OPERATION_FINISH, migrationInfoService.getInfoResult());
			return migrationInfoService;
		}, true);
	}

	/**
	 * Drops all objects (tables, views, procedures, triggers, ...) in the configured
	 * schemas. The schemas are cleaned in the order specified by
	 * {@link Configuration#getSchemas()}.
	 * <p>
	 * Clean is disabled by default. Enable it with
	 * {@link FluentConfiguration#cleanDisabled(boolean)}.
	 * @return an object summarising the actions taken.
	 * @throws FlyliteException when clean failed or is disabled.
	 */
	public CleanResult clean() {
		return executor.execute(ctx -> {
			CleanResult cleanResult = new DbClean(ctx.database(), ctx.schemaHistory(), ctx.defaultSchema(),
					ctx.schemas(), ctx.callbackExecutor(), configuration)
				.clean();
			ctx.callbackExecutor().onOperationFinishEvent(Event.AFTER_CLEAN_OPERATION_FINISH, cleanResult);
			return cleanResult;
		}, false);
	}

	/**
	 * Validates the applied migrations against the available ones.
	 * <p>
	 * Validation fails if differences in migration names, types or checksums are found,
	 * versions have been applied that aren't resolved locally anymore or versions have
	 * been resolved that haven't been applied yet.
	 * @throws FlyliteValidateException when the validation failed.
	 */
	public void validate() throws FlyliteException {
		ValidateResult validateResult = validateWithResult();
		if (!validateResult.validationSuccessful) {
			throw new FlyliteValidateException(validateResult.errorDetails, validateResult.getAllErrorMessages());
		}
	}

	/**
	 * Validates the applied migrations against the available ones and returns the result
	 * instead of throwing on failure.
	 * @return validation result.
	 * @throws FlyliteException when the validation could not be run.
	 */
	public ValidateResult validateWithResult() throws FlyliteException {
		return executor.execute(ctx -> {
			ValidateResult validateResult = doValidate(ctx, configuration.getIgnoreMigrationPatterns());
			ctx.callbackExecutor().onOperationFinishEvent(Event.AFTER_VALIDATE_OPERATION_FINISH, validateResult);
			return validateResult;
		}, true);
	}

	/**
	 * Baselines an existing database, excluding all migrations up to and including
	 * baselineVersion.
	 * @return an object summarising the actions taken.
	 * @throws FlyliteException when the schema baseline failed.
	 */
	public BaselineResult baseline() throws FlyliteException {
		return executor.execute(ctx -> {
			if (configuration.isCreateSchemas()) {
				new DbSchemas(ctx.database(), ctx.schemas(), ctx.schemaHistory(), ctx.callbackExecutor()).create(true);
			}
			else {
				LOG.warn("The configuration option 'createSchemas' is false.\n"
						+ "Even though Flylite is configured not to create any schemas, the schema history table still needs a schema to reside in.\n"
						+ "You must manually create a schema for the schema history table to reside in.");
			}
			BaselineResult baselineResult = doBaseline(ctx);
			ctx.callbackExecutor().onOperationFinishEvent(Event.AFTER_BASELINE_OPERATION_FINISH, baselineResult);
			return baselineResult;
		}, false);
	}

	/**
	 * Repairs the schema history table. This will perform the following actions:
	 * <ul>
	 * <li>Remove any failed migrations on databases without DDL transactions</li>
	 * <li>Realign the checksums, descriptions and types of the applied migrations with
	 * the ones of the available migrations</li>
	 * <li>Mark all missing migrations as deleted</li>
	 * </ul>
	 * @return an object summarising the actions taken.
	 * @throws FlyliteException when the schema history table repair failed.
	 */
	public RepairResult repair() throws FlyliteException {
		return executor.execute(ctx -> {
			RepairResult repairResult = new DbRepair(ctx.database(), ctx.migrationResolver(), ctx.schemaHistory(),
					ctx.callbackExecutor(), configuration)
				.repair();
			ctx.callbackExecutor().onOperationFinishEvent(Event.AFTER_REPAIR_OPERATION_FINISH, repairResult);
			return repairResult;
		}, true);
	}

	private ValidateResult doValidate(FlyliteExecutor.Context ctx, List<ValidatePattern> ignorePatterns) {
		return new DbValidate(ctx.database(), ctx.schemaHistory(), ctx.defaultSchema(), ctx.migrationResolver(),
				configuration, ctx.callbackExecutor(), ignorePatterns)
			.validate();
	}

	private BaselineResult doBaseline(FlyliteExecutor.Context ctx) {
		return new DbBaseline(ctx.schemaHistory(), configuration.getBaselineVersion(),
				configuration.getBaselineDescription(), ctx.callbackExecutor(), ctx.database())
			.baseline();
	}

}
