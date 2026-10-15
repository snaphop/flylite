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
package com.snaphop.flylite.internal.schemahistory;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.internal.logging.Log;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.MigrationVersion;
import com.snaphop.flylite.internal.command.CommandResultFactory;
import com.snaphop.flylite.api.output.RepairResult;
import com.snaphop.flylite.api.resolver.ResolvedMigration;
import com.snaphop.flylite.internal.schemahistory.AppliedMigration;
import com.snaphop.flylite.api.MigrationType;
import com.snaphop.flylite.internal.database.base.Connection;
import com.snaphop.flylite.internal.database.base.Database;
import com.snaphop.flylite.internal.database.base.Table;
import com.snaphop.flylite.internal.exception.FlyliteSqlException;
import com.snaphop.flylite.internal.jdbc.ExecutionTemplateFactory;
import com.snaphop.flylite.internal.jdbc.JdbcNullTypes;
import com.snaphop.flylite.internal.jdbc.JdbcTemplate;
import com.snaphop.flylite.internal.sqlscript.SqlScriptExecutorFactory;
import com.snaphop.flylite.internal.sqlscript.SqlScriptFactory;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Supports reading and writing to the schema history table.
 */
class JdbcTableSchemaHistory extends SchemaHistory {

	private static final Log LOG = Log.of(JdbcTableSchemaHistory.class);

	private final SqlScriptExecutorFactory sqlScriptExecutorFactory;

	private final SqlScriptFactory sqlScriptFactory;

	/**
	 * The database to use.
	 */
	private final Database<?> database;

	/**
	 * Connection with access to the database.
	 */
	private final Connection<?> connection;

	private final JdbcTemplate jdbcTemplate;

	/**
	 * Applied migration cache.
	 */
	private final List<AppliedMigration> cache = new ArrayList<>();

	/**
	 * Creates a new instance of the schema history table support.
	 * @param database The database to use.
	 * @param table The schema history table used by Flylite.
	 */
	JdbcTableSchemaHistory(SqlScriptExecutorFactory sqlScriptExecutorFactory, SqlScriptFactory sqlScriptFactory,
			Database<?> database, Table<?, ?> table) {
		super(table);
		this.sqlScriptExecutorFactory = sqlScriptExecutorFactory;
		this.sqlScriptFactory = sqlScriptFactory;
		this.database = database;
		this.connection = database.getMainConnection();
		this.jdbcTemplate = connection.getJdbcTemplate();
	}

	@Override
	public void clearCache() {
		cache.clear();
	}

	@Override
	public boolean exists() {
		connection.restoreOriginalState();
		return table.exists();
	}

	@Override
	public void create(final boolean baseline) {
		connection.lock(table, () -> {
			int retries = 0;
			while (!exists()) {
				if (retries == 0) {
					LOG.info("Creating Schema History table " + table + (baseline ? " with baseline" : "") + " ...");
				}
				try {
					ExecutionTemplateFactory.createExecutionTemplate(connection.getJdbcConnection(), database)
						.execute(() -> {
							sqlScriptExecutorFactory.createSqlScriptExecutor(connection.getJdbcConnection())
								.execute(database.getCreateScript(sqlScriptFactory, table, baseline));
							LOG.debug("Created Schema History table " + table + (baseline ? " with baseline" : ""));
							return null;
						});
				}
				catch (FlyliteException e) {
					if (++retries >= 10) {
						throw e;
					}
					try {
						LOG.debug("Schema History table creation failed. Retrying in 1 sec ...");
						Thread.sleep(1000);
					}
					catch (InterruptedException e1) {
						Thread.currentThread().interrupt();
						throw new FlyliteException("Interrupted while creating the schema history table", e1);
					}
				}
				// Ignore
			}
			return null;
		});
	}

	@Override
	public <T> T lock(Callable<T> callable) {
		connection.restoreOriginalState();
		return connection.lock(table, callable);
	}

	@Override
	protected void doAddAppliedMigration(int installedRank, @Nullable MigrationVersion version, String description,
			MigrationType type, String script, @Nullable Integer checksum, int executionTime, boolean success) {
		boolean tableIsLocked = false;
		connection.restoreOriginalState();
		// Lock again for databases with no clean DDL transactions like Oracle
		// to prevent implicit commits from triggering deadlocks
		// in highly concurrent environments
		if (!database.supportsDdlTransactions()) {
			table.lock();
			tableIsLocked = true;
		}
		try {
			String versionStr = version == null ? null : version.toString();
			if (!database.supportsEmptyMigrationDescription() && "".equals(description)) {
				description = NO_DESCRIPTION_MARKER;
			}
			Object versionObj = versionStr == null ? JdbcNullTypes.StringNull : versionStr;
			Object checksumObj = checksum == null ? JdbcNullTypes.IntegerNull : checksum;
			jdbcTemplate.update(database.getInsertStatement(table), installedRank, versionObj, description, type.name(),
					script, checksumObj, database.getInstalledBy(), executionTime, success);
			LOG.debug("Schema History table " + table + " successfully updated to reflect changes");
		}
		catch (SQLException e) {
			throw new FlyliteSqlException(
					"Unable to insert row for version \'" + version + "\' in Schema History table " + table, e);
		}
		finally {
			if (tableIsLocked) {
				table.unlock();
			}
		}
	}

	@Override
	public List<AppliedMigration> allAppliedMigrations() {
		if (!exists()) {
			LOG.info(String.format("Schema history table %s does not exist yet", table.toString()));
			return new ArrayList<>();
		}
		refreshCache();
		return cache;
	}

	private void refreshCache() {
		int maxCachedInstalledRank = cache.isEmpty() ? -1 : cache.get(cache.size() - 1).getInstalledRank();
		String query = database.getSelectStatement(table);
		try {
			cache.addAll(jdbcTemplate.query(query, rs -> {
				Integer checksum = rs.getInt("checksum");
				if (rs.wasNull()) {
					checksum = null;
				}
				String version = rs.getString("version");
				return new AppliedMigration(rs.getInt("installed_rank"),
						version == null ? null : MigrationVersion.fromVersion(version), notNull(rs, "description"),
						MigrationType.fromString(notNull(rs, "type")), notNull(rs, "script"), checksum,
						rs.getTimestamp("installed_on"), rs.getString("installed_by"), rs.getInt("execution_time"),
						rs.getBoolean("success"));
			}, maxCachedInstalledRank));
		}
		catch (SQLException e) {
			throw new FlyliteSqlException(
					"Error while retrieving the list of applied migrations from Schema History table " + table, e);
		}
	}

	private static String notNull(ResultSet rs, String column) throws SQLException {
		String value = rs.getString(column);
		if (value == null) {
			throw new SQLException("Unexpected null " + column + " in schema history table");
		}
		return value;
	}

	@Override
	public boolean removeFailedMigrations(RepairResult repairResult) {
		if (!exists()) {
			LOG.info("Repair of failed migration in Schema History table " + table
					+ " not necessary as table doesn\'t exist.");
			return false;
		}
		List<AppliedMigration> appliedMigrations = allAppliedMigrations();
		boolean failed = appliedMigrations.stream().anyMatch(am -> !am.isSuccess());
		if (!failed) {
			LOG.info("Repair of failed migration in Schema History table " + table
					+ " not necessary. No failed migration detected.");
			return false;
		}
		try {
			appliedMigrations.stream()
				.filter(am -> !am.isSuccess())
				.forEach(am -> repairResult.migrationsRemoved.add(CommandResultFactory.createRepairOutput(am)));
			for (AppliedMigration appliedMigration : appliedMigrations) {
				MigrationVersion version = appliedMigration.getVersion();
				String deleteFailed = "DELETE FROM " + table + " WHERE " + database.quote("success") + " = "
						+ database.getBooleanFalse() + " AND ";
				if (version != null) {
					jdbcTemplate.execute(deleteFailed + database.quote("version") + " = ?", version.getVersion());
				}
				else {
					jdbcTemplate.execute(deleteFailed + database.quote("description") + " = ?",
							appliedMigration.getDescription());
				}
			}
			clearCache();
		}
		catch (SQLException e) {
			throw new FlyliteSqlException("Unable to repair Schema History table " + table, e);
		}
		return true;
	}

	@Override
	public void update(AppliedMigration appliedMigration, ResolvedMigration resolvedMigration) {
		connection.restoreOriginalState();
		clearCache();
		MigrationVersion version = appliedMigration.getVersion();
		String description = resolvedMigration.getDescription();
		Integer checksum = resolvedMigration.getChecksum();
		MigrationType type = appliedMigration.getType().isSynthetic() ? appliedMigration.getType()
				: resolvedMigration.getType();
		LOG.info("Repairing Schema History table for version " + version + " (Description: " + description + ", Type: "
				+ type + ", Checksum: " + checksum + ")  ...");
		if (!database.supportsEmptyMigrationDescription() && "".equals(description)) {
			description = NO_DESCRIPTION_MARKER;
		}
		Object checksumObj = checksum == null ? JdbcNullTypes.IntegerNull : checksum;
		try {
			jdbcTemplate.update("UPDATE " + table + " SET " + database.quote("description") + "=? , "
					+ database.quote("type") + "=? , " + database.quote("checksum") + "=?" + " WHERE "
					+ database.quote("installed_rank") + "=?", description, type.name(), checksumObj,
					appliedMigration.getInstalledRank());
		}
		catch (SQLException e) {
			throw new FlyliteSqlException("Unable to repair Schema History table " + table + " for version " + version,
					e);
		}
	}

	@Override
	public void delete(AppliedMigration appliedMigration) {
		connection.restoreOriginalState();
		clearCache();
		MigrationVersion version = appliedMigration.getVersion();
		String versionStr = version == null ? null : version.toString();
		if (version == null) {
			LOG.info("Repairing Schema History table for description \"" + appliedMigration.getDescription()
					+ "\" (Marking as DELETED)  ...");
		}
		else {
			LOG.info("Repairing Schema History table for version \"" + version + "\" (Marking as DELETED)  ...");
		}
		Object versionObj = versionStr == null ? JdbcNullTypes.StringNull : versionStr;
		Object checksumObj = appliedMigration.getChecksum() == null ? JdbcNullTypes.IntegerNull
				: appliedMigration.getChecksum();
		try {
			jdbcTemplate.update(database.getInsertStatement(table), calculateInstalledRank(appliedMigration.getType()),
					versionObj, appliedMigration.getDescription(), "DELETE", appliedMigration.getScript(), checksumObj,
					database.getInstalledBy(), 0, appliedMigration.isSuccess());
		}
		catch (SQLException e) {
			throw new FlyliteSqlException("Unable to repair Schema History table " + table + " for version " + version,
					e);
		}
	}

}
