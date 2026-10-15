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
package com.snaphop.flylite.internal.command;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.internal.logging.Log;
import com.snaphop.flylite.api.ErrorCode;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.MigrationInfo;
import com.snaphop.flylite.api.MigrationState;
import com.snaphop.flylite.api.MigrationVersion;
import com.snaphop.flylite.api.callback.Event;
import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.api.executor.Context;
import com.snaphop.flylite.internal.command.CommandResultFactory;
import com.snaphop.flylite.api.output.MigrateResult;
import com.snaphop.flylite.api.resolver.ResolvedMigration;
import com.snaphop.flylite.internal.callback.CallbackExecutor;
import com.snaphop.flylite.internal.database.base.Connection;
import com.snaphop.flylite.internal.database.base.Database;
import com.snaphop.flylite.internal.database.base.Schema;
import com.snaphop.flylite.internal.info.MigrationInfoImpl;
import com.snaphop.flylite.internal.info.MigrationInfoServiceImpl;
import com.snaphop.flylite.internal.jdbc.ExecutionTemplateFactory;
import com.snaphop.flylite.internal.resolver.CompositeMigrationResolver;
import com.snaphop.flylite.internal.schemahistory.SchemaHistory;
import com.snaphop.flylite.internal.util.*;
import java.sql.SQLException;
import java.util.*;

public class DbMigrate {

	private static final Log LOG = Log.of(DbMigrate.class);

	private final Database<?> database;

	private final SchemaHistory schemaHistory;

	/**
	 * The schema containing the schema history table.
	 */
	private final Schema<?, ?> schema;

	private final CompositeMigrationResolver migrationResolver;

	private final Configuration configuration;

	private final CallbackExecutor callbackExecutor;

	/**
	 * The connection to use to perform the actual database migrations.
	 */
	private final Connection<?> connectionUserObjects;

	private final MigrateResult migrateResult;

	/**
	 * This is used to remember the type of migration between calls to migrateGroup().
	 */
	private boolean isPreviousVersioned;

	private final List<ResolvedMigration> appliedResolvedMigrations = new ArrayList<>();

	public DbMigrate(Database<?> database, SchemaHistory schemaHistory, Schema<?, ?> schema,
			CompositeMigrationResolver migrationResolver, Configuration configuration,
			CallbackExecutor callbackExecutor) {
		this.database = database;
		this.connectionUserObjects = database.getMigrationConnection();
		this.schemaHistory = schemaHistory;
		this.schema = schema;
		this.migrationResolver = migrationResolver;
		this.configuration = configuration;
		this.callbackExecutor = callbackExecutor;
		this.migrateResult = CommandResultFactory.createMigrateResult(database.getCatalog(), configuration);
	}

	/**
	 * Starts the actual migration.
	 */
	public MigrateResult migrate() throws FlyliteException {
		callbackExecutor.onMigrateEvent(Event.BEFORE_MIGRATE);
		int count;
		try {
			StopWatch stopWatch = new StopWatch();
			stopWatch.start();
			count = configuration.isGroup() ?
			// When group is active, start the transaction boundary early to
			// ensure that all changes to the schema history table are either committed or
			// rolled back atomically.
					schemaHistory.lock(this::migrateAll) :
					// For all regular cases, proceed with the migration as usual.
					migrateAll();
			stopWatch.stop();
			migrateResult.targetSchemaVersion = getTargetVersion();
			migrateResult.migrationsExecuted = count;
			logSummary(count, stopWatch.getTotalTimeMillis(), migrateResult.targetSchemaVersion);
		}
		catch (FlyliteException e) {
			callbackExecutor.onMigrateEvent(Event.AFTER_MIGRATE_ERROR);
			throw e;
		}
		if (count > 0) {
			callbackExecutor.onMigrateEvent(Event.AFTER_MIGRATE_APPLIED);
		}
		callbackExecutor.onMigrateEvent(Event.AFTER_MIGRATE);
		return migrateResult;
	}

	private @Nullable String getTargetVersion() {
		if (!migrateResult.migrations.isEmpty()) {
			for (int i = migrateResult.migrations.size() - 1; i >= 0; i--) {
				String targetVersion = migrateResult.migrations.get(i).version();
				if (!targetVersion.isEmpty()) {
					return targetVersion;
				}
			}
		}
		return null;
	}

	private int migrateAll() {
		int total = 0;
		isPreviousVersioned = true;
		while (true) {
			final boolean firstRun = total == 0;
			int count =
					// With group active a lock on the schema history table has already
					// been acquired.
					configuration.isGroup() ? migrateGroup(firstRun) :
					// Otherwise acquire the lock now. The lock will be released at the
					// end of each migration.
							schemaHistory.lock(() -> migrateGroup(firstRun));
			migrateResult.migrationsExecuted += count;
			total += count;
			if (count == 0) {
				// No further migrations available
				break;
			}
			else if (MigrationVersion.NEXT.equals(configuration.getTarget())) {
				// With target=next we only execute one migration
				break;
			}
		}
		if (isPreviousVersioned) {
			callbackExecutor.onMigrateEvent(Event.AFTER_VERSIONED);
		}
		return total;
	}

	/**
	 * Migrate a group of one (group = false) or more (group = true) migrations.
	 * @param firstRun Whether this is the first time this code runs in this migration
	 * run.
	 * @return The number of newly applied migrations.
	 */
	private Integer migrateGroup(boolean firstRun) {
		MigrationInfoServiceImpl infoService = new MigrationInfoServiceImpl(migrationResolver, schemaHistory, database,
				configuration, configuration.getTarget(), configuration.isOutOfOrder(),
				ValidatePatternUtils.getIgnoreAllPattern());
		infoService.refresh();
		MigrationInfo current = infoService.current();
		MigrationVersion currentSchemaVersion = current == null ? MigrationVersion.EMPTY : current.getVersion();
		if (firstRun) {
			LOG.info("Current version of schema " + schema + ": " + currentSchemaVersion);
			MigrationVersion schemaVersionToOutput = currentSchemaVersion == null ? MigrationVersion.EMPTY
					: currentSchemaVersion;
			migrateResult.initialSchemaVersion = schemaVersionToOutput.getVersion();
			if (configuration.isOutOfOrder()) {
				String outOfOrderWarning = "outOfOrder mode is active. Migration of schema " + schema
						+ " may not be reproducible.";
				LOG.warn(outOfOrderWarning);
				migrateResult.addWarning(outOfOrderWarning);
			}
		}
		MigrationInfo[] future = infoService.future();
		if (future.length > 0) {
			List<MigrationInfo> resolved = Arrays.asList(infoService.resolved());
			Collections.reverse(resolved);
			if (resolved.isEmpty()) {
				LOG.error("Schema " + schema + " has version " + currentSchemaVersion
						+ ", but no migration could be resolved in the configured locations !");
			}
			else {
				for (MigrationInfo migrationInfo : resolved) {
					// Only consider versioned migrations
					if (migrationInfo.getVersion() != null) {
						LOG.warn("Schema " + schema + " has a version (" + currentSchemaVersion
								+ ") that is newer than the latest available migration (" + migrationInfo.getVersion()
								+ ") !");
						break;
					}
				}
			}
		}
		MigrationInfoImpl[] failed = infoService.failed();
		if (failed.length > 0) {
			if ((failed.length == 1) && (failed[0].getState() == MigrationState.FUTURE_FAILED)
					&& ValidatePatternUtils.isFutureIgnored(configuration.getIgnoreMigrationPatterns())) {
				LOG.warn("Schema " + schema + " contains a failed future migration to version " + failed[0].getVersion()
						+ " !");
			}
			else {
				final boolean inTransaction = failed[0].canExecuteInTransaction();
				if (failed[0].getVersion() == null) {
					throw new FlyliteMigrateException(failed[0], "Schema " + schema
							+ " contains a failed repeatable migration (" + doQuote(failed[0].getDescription()) + ") !",
							inTransaction);
				}
				throw new FlyliteMigrateException(failed[0],
						"Schema " + schema + " contains a failed migration to version " + failed[0].getVersion() + " !",
						inTransaction);
			}
		}
		LinkedHashMap<MigrationInfoImpl, Boolean> group = new LinkedHashMap<>();
		for (MigrationInfoImpl pendingMigration : infoService.pending()) {
			ResolvedMigration pendingResolved = pendingMigration.getResolvedMigration();
			if (pendingResolved != null && appliedResolvedMigrations.contains(pendingResolved)) {
				continue;
			}
			MigrationVersion pendingVersion = pendingMigration.getVersion();
			boolean isOutOfOrder = pendingVersion != null && pendingVersion.compareTo(currentSchemaVersion) < 0;
			group.put(pendingMigration, isOutOfOrder);
			if (!configuration.isGroup()) {
				// Only include one pending migration if group is disabled
				break;
			}
		}
		if (!group.isEmpty()) {
			boolean skipExecutingMigrations = false;
			applyMigrations(group, skipExecutingMigrations);
		}
		return group.size();
	}

	private void logSummary(int migrationSuccessCount, long executionTime, @Nullable String targetVersion) {
		if (migrationSuccessCount == 0) {
			LOG.info("Schema " + schema + " is up to date. No migration necessary.");
			return;
		}
		String targetText = (targetVersion != null) ? ", now at version v" + targetVersion : "";
		String migrationText = (migrationSuccessCount == 1) ? "migration" : "migrations";
		LOG.info("Successfully applied " + migrationSuccessCount + " " + migrationText + " to schema " + schema
				+ targetText + " (execution time " + TimeFormat.format(executionTime) + ")");
	}

	/**
	 * Applies this migration to the database. The migration state and the execution time
	 * are updated accordingly.
	 */
	private void applyMigrations(final Map<MigrationInfoImpl, Boolean> group, boolean skipExecutingMigrations) {
		boolean executeGroupInTransaction = isExecuteGroupInTransaction(group);
		final StopWatch stopWatch = new StopWatch();
		try {
			if (executeGroupInTransaction) {
				ExecutionTemplateFactory.createExecutionTemplate(connectionUserObjects.getJdbcConnection(), database)
					.execute(() -> {
						doMigrateGroup(group, stopWatch, skipExecutingMigrations, true);
						return null;
					});
			}
			else {
				doMigrateGroup(group, stopWatch, skipExecutingMigrations, false);
			}
		}
		catch (FlyliteMigrateException e) {
			MigrationInfo migration = e.getMigration();
			String failedMsg = "Migration of "
					+ toMigrationText(migration, e.isExecutableInTransaction(), e.isOutOfOrder()) + " failed!";
			if (database.supportsDdlTransactions() && executeGroupInTransaction) {
				LOG.error(failedMsg + " Changes successfully rolled back.");
			}
			else {
				LOG.error(failedMsg + " Please restore backups and roll back database and code!");
				stopWatch.stop();
				int executionTime = (int) stopWatch.getTotalTimeMillis();
				schemaHistory.addAppliedMigration(migration.getVersion(), migration.getDescription(),
						migration.getType(), migration.getScript(), migration.getChecksum(), executionTime, false);
			}
			throw e;
		}
	}

	private boolean isExecuteGroupInTransaction(Map<MigrationInfoImpl, Boolean> group) {
		boolean executeGroupInTransaction = true;
		boolean first = true;
		for (Map.Entry<MigrationInfoImpl, Boolean> entry : group.entrySet()) {
			ResolvedMigration resolvedMigration = Objects.requireNonNull(entry.getKey().getResolvedMigration());
			boolean inTransaction = resolvedMigration.getExecutor().canExecuteInTransaction();
			if (first) {
				executeGroupInTransaction = inTransaction;
				first = false;
				continue;
			}
			if (!configuration.isMixed() && executeGroupInTransaction != inTransaction) {
				throw new FlyliteMigrateException(entry.getKey(),
						"Detected both transactional and non-transactional migrations within the same migration group"
								+ " (even though mixed is false). First offending migration: "
								+ doQuote((resolvedMigration.getVersion() == null ? "" : resolvedMigration.getVersion())
										+ (StringUtils.hasLength(resolvedMigration.getDescription())
												? " " + resolvedMigration.getDescription() : ""))
								+ (inTransaction ? "" : " [non-transactional]"),
						inTransaction);
			}
			executeGroupInTransaction &= inTransaction;
		}
		return executeGroupInTransaction;
	}

	private void doMigrateGroup(Map<MigrationInfoImpl, Boolean> group, StopWatch stopWatch,
			boolean skipExecutingMigrations, boolean isExecuteInTransaction) {
		Context context = new Context() {
			@Override
			public Configuration getConfiguration() {
				return configuration;
			}

			@Override
			public java.sql.Connection getConnection() {
				return connectionUserObjects.getJdbcConnection();
			}
		};
		for (Map.Entry<MigrationInfoImpl, Boolean> entry : group.entrySet()) {
			final MigrationInfoImpl migration = entry.getKey();
			boolean isOutOfOrder = entry.getValue();
			final String migrationText = toMigrationText(migration, migration.canExecuteInTransaction(), isOutOfOrder);
			stopWatch.start();
			if (isPreviousVersioned && migration.getVersion() == null) {
				callbackExecutor.onMigrateEvent(Event.AFTER_VERSIONED);
				callbackExecutor.onMigrateEvent(Event.BEFORE_REPEATABLES);
				isPreviousVersioned = false;
			}
			if (skipExecutingMigrations) {
				LOG.debug("Skipping execution of migration of " + migrationText);
			}
			else {
				LOG.debug("Starting migration of " + migrationText + " ...");
				connectionUserObjects.restoreOriginalState();
				connectionUserObjects.changeCurrentSchemaTo(schema);
				try {
					callbackExecutor.setMigrationInfo(migration);
					callbackExecutor.onEachMigrateEvent(Event.BEFORE_EACH_MIGRATE);
					try {
						LOG.info("Migrating " + migrationText);
						// With single connection databases we need to manually disable
						// the transaction for the
						// migration as it is turned on for schema history changes
						boolean oldAutoCommit = context.getConnection().getAutoCommit();
						if (database.useSingleConnection() && !isExecuteInTransaction) {
							context.getConnection().setAutoCommit(true);
						}
						ResolvedMigration resolvedMigration = Objects.requireNonNull(migration.getResolvedMigration());
						resolvedMigration.getExecutor().execute(context);
						if (database.useSingleConnection() && !isExecuteInTransaction) {
							context.getConnection().setAutoCommit(oldAutoCommit);
						}
						appliedResolvedMigrations.add(resolvedMigration);
					}
					catch (FlyliteException e) {
						callbackExecutor.onEachMigrateEvent(Event.AFTER_EACH_MIGRATE_ERROR);
						throw new FlyliteMigrateException(migration, isOutOfOrder, e,
								migration.canExecuteInTransaction());
					}
					catch (SQLException e) {
						callbackExecutor.onEachMigrateEvent(Event.AFTER_EACH_MIGRATE_ERROR);
						throw new FlyliteMigrateException(migration, isOutOfOrder, e,
								migration.canExecuteInTransaction());
					}
					LOG.debug("Successfully completed migration of " + migrationText);
					callbackExecutor.onEachMigrateEvent(Event.AFTER_EACH_MIGRATE);
				}
				finally {
					callbackExecutor.setMigrationInfo(null);
				}
			}
			stopWatch.stop();
			int executionTime = (int) stopWatch.getTotalTimeMillis();
			migrateResult.migrations.add(CommandResultFactory.createMigrateOutput(migration, executionTime));
			schemaHistory.addAppliedMigration(migration.getVersion(), migration.getDescription(), migration.getType(),
					migration.getScript(), Objects.requireNonNull(migration.getResolvedMigration()).getChecksum(),
					executionTime, true);
		}
	}

	private String toMigrationText(MigrationInfo migration, boolean canExecuteInTransaction, boolean isOutOfOrder) {
		final String migrationText;
		if (migration.getVersion() != null) {
			migrationText = "schema " + schema + " to version " + doQuote(migration.getVersion()
					+ (StringUtils.hasLength(migration.getDescription()) ? " - " + migration.getDescription() : ""))
					+ (isOutOfOrder ? " [out of order]" : "") + (canExecuteInTransaction ? "" : " [non-transactional]");
		}
		else {
			migrationText = "schema " + schema + " with repeatable migration " + doQuote(migration.getDescription())
					+ (canExecuteInTransaction ? "" : " [non-transactional]");
		}
		return migrationText;
	}

	private String doQuote(String text) {
		return "\"" + text + "\"";
	}

	public static class FlyliteMigrateException extends FlyliteException {

		private final MigrationInfo migration;

		private final boolean executableInTransaction;

		private final boolean outOfOrder;

		FlyliteMigrateException(MigrationInfo migration, boolean outOfOrder, SQLException e,
				boolean canExecuteInTransaction) {
			super(ExceptionUtils.toMessage(e), e);
			this.migration = migration;
			this.outOfOrder = outOfOrder;
			this.executableInTransaction = canExecuteInTransaction;
		}

		FlyliteMigrateException(MigrationInfo migration, String message, boolean canExecuteInTransaction) {
			super(message);
			this.outOfOrder = false;
			this.migration = migration;
			this.executableInTransaction = canExecuteInTransaction;
		}

		FlyliteMigrateException(MigrationInfo migration, boolean outOfOrder, FlyliteException e,
				boolean canExecuteInTransaction) {
			super(e.getMessage(), e);
			this.migration = migration;
			this.outOfOrder = outOfOrder;
			this.executableInTransaction = canExecuteInTransaction;
		}

		public MigrationInfo getMigration() {
			return this.migration;
		}

		public boolean isExecutableInTransaction() {
			return this.executableInTransaction;
		}

		public boolean isOutOfOrder() {
			return this.outOfOrder;
		}

	}

}
