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
package com.snaphop.flylite.internal.database.base;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.internal.logging.Log;
import com.snaphop.flylite.api.MigrationType;
import com.snaphop.flylite.api.MigrationVersion;
import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.internal.database.DatabaseType;
import com.snaphop.flylite.internal.exception.FlyliteDbUpgradeRequiredException;
import com.snaphop.flylite.internal.exception.FlyliteSqlException;
import com.snaphop.flylite.internal.jdbc.JdbcConnectionFactory;
import com.snaphop.flylite.internal.jdbc.JdbcTemplate;
import com.snaphop.flylite.internal.jdbc.JdbcUtils;
import com.snaphop.flylite.internal.resource.StringResource;
import com.snaphop.flylite.internal.sqlscript.Delimiter;
import com.snaphop.flylite.internal.sqlscript.SqlScript;
import com.snaphop.flylite.internal.sqlscript.SqlScriptFactory;
import com.snaphop.flylite.internal.util.AbbreviationUtils;
import com.snaphop.flylite.internal.util.StringUtils;
import java.io.Closeable;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

/**
 * Abstraction for database-specific functionality.
 */
public abstract class Database<C extends Connection<?>> implements Closeable {

	private static final Log LOG = Log.of(Database.class);

	protected final DatabaseType databaseType;

	protected final Configuration configuration;

	protected final JdbcConnectionFactory jdbcConnectionFactory;

	protected final DatabaseMetaData jdbcMetaData;

	/**
	 * The main JDBC connection, without any wrapping.
	 */
	protected final java.sql.Connection rawMainJdbcConnection;

	protected JdbcTemplate jdbcTemplate;

	private @Nullable C migrationConnection;

	private @Nullable C eventConnection;

	private @Nullable C mainConnection;

	/**
	 * The 'major.minor' version of this database.
	 */
	private @Nullable MigrationVersion version;

	/**
	 * The user who applied the migrations.
	 */
	private @Nullable String installedBy;

	public Database(Configuration configuration, JdbcConnectionFactory jdbcConnectionFactory) {
		this.databaseType = jdbcConnectionFactory.getDatabaseType();
		this.configuration = configuration;
		this.rawMainJdbcConnection = jdbcConnectionFactory.openConnection();
		try {
			this.jdbcMetaData = rawMainJdbcConnection.getMetaData();
		}
		catch (SQLException e) {
			throw new FlyliteSqlException("Unable to get metadata for connection", e);
		}
		this.jdbcTemplate = new JdbcTemplate(rawMainJdbcConnection, databaseType);
		this.jdbcConnectionFactory = jdbcConnectionFactory;
	}

	/**
	 * Retrieves a Flylite Connection for this JDBC connection.
	 */
	private C getConnection(java.sql.Connection connection) {
		return doGetConnection(connection);
	}

	/**
	 * Retrieves a Flylite Connection for this JDBC connection.
	 */
	protected abstract C doGetConnection(java.sql.Connection connection);

	/**
	 * Ensure Flylite supports this version of this database.
	 */
	public abstract void ensureSupported();

	/**
	 * Returns the 'major.minor' version of this database.
	 * @return The 'major.minor' version of this database.
	 */
	public final MigrationVersion getVersion() {
		MigrationVersion v = version;
		if (v == null) {
			v = determineVersion();
			version = v;
		}
		return v;
	}

	protected final void ensureDatabaseIsRecentEnough(String oldestSupportedVersion) {
		if (!getVersion().isAtLeast(oldestSupportedVersion)) {
			throw new FlyliteDbUpgradeRequiredException(databaseType, computeVersionDisplayName(getVersion()),
					computeVersionDisplayName(MigrationVersion.fromVersion(oldestSupportedVersion)));
		}
	}

	protected final void recommendFlyliteUpgradeIfNecessaryForMajorVersion(String newestSupportedVersion) {
		if (getVersion().isMajorNewerThan(newestSupportedVersion)) {
			recommendFlyliteUpgrade(newestSupportedVersion);
		}
	}

	private void recommendFlyliteUpgrade(String newestSupportedVersion) {
		String message = databaseType + " " + computeVersionDisplayName(getVersion())
				+ " is newer than the latest version Flylite has been tested with (" + newestSupportedVersion + ").";
		LOG.warn(message);
	}

	/**
	 * Compute the user-friendly display name for this database version.
	 */
	protected String computeVersionDisplayName(MigrationVersion version) {
		return version.toString();
	}

	public Delimiter getDefaultDelimiter() {
		return Delimiter.SEMICOLON;
	}

	/**
	 * Returns the name of the database, by default as determined by JDBC.
	 * @return The name of the database, by default as determined by JDBC.
	 */
	public final String getCatalog() {
		try {
			return doGetCatalog();
		}
		catch (SQLException e) {
			throw new FlyliteSqlException("Error retrieving the database name", e);
		}
	}

	protected String doGetCatalog() throws SQLException {
		return getMainConnection().getJdbcConnection().getCatalog();
	}

	public final String getCurrentUser() {
		try {
			return doGetCurrentUser();
		}
		catch (SQLException e) {
			throw new FlyliteSqlException("Error retrieving the database user", e);
		}
	}

	protected String doGetCurrentUser() throws SQLException {
		return jdbcMetaData.getUserName();
	}

	public abstract boolean supportsDdlTransactions();

	/**
	 * Returns the representation of the value {@code true} in a boolean column.
	 * @return The representation of the value {@code true} in a boolean column.
	 */
	public abstract String getBooleanTrue();

	/**
	 * Returns the representation of the value {@code false} in a boolean column.
	 * @return The representation of the value {@code false} in a boolean column.
	 */
	public abstract String getBooleanFalse();

	/**
	 * Quotes these identifiers for use in SQL queries. Multiple identifiers will be
	 * quoted and separated by a dot.
	 */
	public final String quote(String... identifiers) {
		StringBuilder result = new StringBuilder();
		boolean first = true;
		for (String identifier : identifiers) {
			if (!first) {
				result.append(".");
			}
			first = false;
			result.append(doQuote(identifier));
		}
		return result.toString();
	}

	/**
	 * Quotes this identifier for use in SQL queries.
	 */
	public String doQuote(String identifier) {
		return getOpenQuote() + identifier + getCloseQuote();
	}

	protected String getOpenQuote() {
		return "\"";
	}

	protected String getCloseQuote() {
		return "\"";
	}

	protected String getEscapedQuote() {
		return "";
	}

	public String unQuote(String identifier) {
		String open = getOpenQuote();
		String close = getCloseQuote();
		if (!open.equals("") && !close.equals("") && identifier.startsWith(open) && identifier.endsWith(close)) {
			identifier = identifier.substring(open.length(), identifier.length() - close.length());
			if (!getEscapedQuote().equals("")) {
				identifier = StringUtils.replaceAll(identifier, getEscapedQuote(), close);
			}
		}
		return identifier;
	}

	/**
	 * Returns {@code true} if this database uses a catalog to represent a schema, or
	 * @return {@code true} if this database uses a catalog to represent a schema, or
	 * {@code false} if a schema is simply a schema.
	 */
	public abstract boolean catalogIsSchema();

	/**
	 * Returns whether to use a single connection for both schema history table management
	 * @return Whether to use a single connection for both schema history table management
	 * and applying migrations.
	 */
	public boolean useSingleConnection() {
		return false;
	}

	/**
	 * Returns the main connection used to manipulate the schema history.
	 * @return The main connection used to manipulate the schema history.
	 */
	public final C getMainConnection() {
		C c = mainConnection;
		if (c == null) {
			c = getConnection(rawMainJdbcConnection);
			mainConnection = c;
		}
		return c;
	}

	/**
	 * Returns the migration connection used to apply migrations.
	 * @return The migration connection used to apply migrations.
	 */
	public final C getMigrationConnection() {
		C c = migrationConnection;
		if (c == null) {
			c = useSingleConnection() ? getMainConnection() : getConnection(jdbcConnectionFactory.openConnection());
			migrationConnection = c;
		}
		return c;
	}

	/**
	 * Returns the event connection used to handle event callbacks. The reason for
	 * @return The event connection used to handle event callbacks. The reason for
	 * creating an event connection is that if using the migration connection instead, it
	 * may trigger an unwanted commit which breaks any ongoing migration transaction.
	 */
	public final C getEventConnection() {
		C c = eventConnection;
		if (c == null) {
			c = getConnection(jdbcConnectionFactory.openConnection());
			eventConnection = c;
		}
		return c;
	}

	public final boolean hasEventConnection() {
		return eventConnection != null;
	}

	/**
	 * An event connection should be disposed after usage to minimize long-standing
	 * connections.
	 */
	public void disposeEventConnection() {
		C c = eventConnection;
		if (c != null) {
			c.close();
			eventConnection = null;
		}
	}

	/**
	 * Returns the major and minor version of the database.
	 * @return The major and minor version of the database.
	 */
	protected MigrationVersion determineVersion() {
		try {
			return MigrationVersion
				.fromVersion(jdbcMetaData.getDatabaseMajorVersion() + "." + jdbcMetaData.getDatabaseMinorVersion());
		}
		catch (SQLException e) {
			throw new FlyliteSqlException("Unable to determine the major version of the database", e);
		}
	}

	/**
	 * Retrieves the script used to create the schema history table.
	 * @param sqlScriptFactory The factory used to create the SQL script.
	 * @param table The table to create.
	 * @param baseline Whether to include the creation of a baseline marker.
	 */
	public final SqlScript getCreateScript(SqlScriptFactory sqlScriptFactory, Table<?, ?> table, boolean baseline) {
		return sqlScriptFactory.createSqlScript(new StringResource(getRawCreateScript(table, baseline)), false, null);
	}

	public abstract String getRawCreateScript(Table<?, ?> table, boolean baseline);

	public String getInsertStatement(Table<?, ?> table) {
		return "INSERT INTO " + table + " (" + quote("installed_rank") + ", " + quote("version") + ", "
				+ quote("description") + ", " + quote("type") + ", " + quote("script") + ", " + quote("checksum") + ", "
				+ quote("installed_by") + ", " + quote("execution_time") + ", " + quote("success") + ")"
				+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
	}

	public final String getBaselineStatement(Table<?, ?> table) {
		return String.format(getInsertStatement(table).replace("?", "%s"), 1,
				"\'" + configuration.getBaselineVersion() + "\'",
				"\'" + AbbreviationUtils.abbreviateDescription(configuration.getBaselineDescription()) + "\'",
				"\'" + MigrationType.BASELINE + "\'",
				"\'" + AbbreviationUtils.abbreviateScript(configuration.getBaselineDescription()) + "\'", "NULL",
				"\'" + getInstalledBy() + "\'", 0, getBooleanTrue());
	}

	public String getSelectStatement(Table<?, ?> table) {
		return "SELECT " + quote("installed_rank") + "," + quote("version") + "," + quote("description") + ","
				+ quote("type") + "," + quote("script") + "," + quote("checksum") + "," + quote("installed_on") + ","
				+ quote("installed_by") + "," + quote("execution_time") + "," + quote("success") + " FROM " + table
				+ " WHERE " + quote("installed_rank") + " > ?" + " ORDER BY " + quote("installed_rank");
	}

	public final String getInstalledBy() {
		String by = installedBy;
		if (by == null) {
			String configured = configuration.getInstalledBy();
			by = configured == null ? getCurrentUser() : configured;
			installedBy = by;
		}
		return by;
	}

	@Override
	public void close() {
		C migration = migrationConnection;
		if (!useSingleConnection() && migration != null) {
			migration.close();
		}
		C main = mainConnection;
		if (main != null) {
			main.close();
		}
		disposeEventConnection();
		JdbcUtils.closeConnection(rawMainJdbcConnection);
	}

	public DatabaseType getDatabaseType() {
		return databaseType;
	}

	public boolean supportsEmptyMigrationDescription() {
		return true;
	}

	public boolean supportsMultiStatementTransactions() {
		return true;
	}

	/**
	 * Cleans all the objects in this database that need to be cleaned before each schema.
	 */
	public void cleanPreSchemas() {
		try {
			doCleanPreSchemas();
		}
		catch (SQLException e) {
			throw new FlyliteSqlException("Unable to clean database " + this, e);
		}
	}

	/**
	 * Cleans all the objects in this database that need to be cleaned before each schema.
	 * @throws SQLException when the clean failed.
	 */
	protected void doCleanPreSchemas() throws SQLException {
	}

	/**
	 * Cleans all the objects in this database that need to be cleaned after each schema.
	 * @param schemas The list of schemas managed by Flylite.
	 */
	public void cleanPostSchemas(List<Schema<?, ?>> schemas) {
		try {
			doCleanPostSchemas(schemas);
		}
		catch (SQLException e) {
			throw new FlyliteSqlException("Unable to clean schema " + this, e);
		}
	}

	/**
	 * Cleans all the objects in this database that need to be cleaned after each schema.
	 * @param schemas The list of schemas managed by Flylite.
	 * @throws SQLException when the clean failed.
	 */
	protected void doCleanPostSchemas(List<Schema<?, ?>> schemas) throws SQLException {
	}

	public Configuration getConfiguration() {
		return this.configuration;
	}

}
