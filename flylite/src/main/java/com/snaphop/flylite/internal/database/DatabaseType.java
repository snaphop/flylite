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
package com.snaphop.flylite.internal.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.internal.database.base.Database;
import com.snaphop.flylite.internal.database.postgresql.PostgreSQLDatabaseType;
import com.snaphop.flylite.internal.database.sqlite.SQLiteDatabaseType;
import com.snaphop.flylite.internal.jdbc.ExecutionTemplate;
import com.snaphop.flylite.internal.jdbc.JdbcConnectionFactory;
import com.snaphop.flylite.internal.jdbc.JdbcTemplate;
import com.snaphop.flylite.internal.jdbc.TransactionalExecutionTemplate;
import com.snaphop.flylite.internal.logging.Log;
import com.snaphop.flylite.internal.parser.Parser;
import com.snaphop.flylite.internal.parser.ParsingContext;
import com.snaphop.flylite.internal.sqlscript.DefaultSqlScriptExecutor;
import com.snaphop.flylite.internal.sqlscript.ParserSqlScript;
import com.snaphop.flylite.internal.sqlscript.SqlScriptExecutorFactory;
import com.snaphop.flylite.internal.sqlscript.SqlScriptFactory;
import com.snaphop.flylite.internal.sqlscript.SqlScriptMetadata;

/**
 * The databases Flylite supports. Only PostgreSQL and SQLite are supported which is why
 * this is a sealed hierarchy rather than a plugin.
 */
public abstract sealed class DatabaseType permits PostgreSQLDatabaseType, SQLiteDatabaseType {

	private static final Log LOG = Log.of(DatabaseType.class);

	// Don't grab semicolons and ampersands - they have special meaning in URLs
	private static final Pattern DEFAULT_JDBC_CREDENTIALS_PATTERN = Pattern.compile("password=([^;&]*).*",
			Pattern.CASE_INSENSITIVE);

	/**
	 * Application name reported to databases that support it.
	 */
	protected static final String APPLICATION_NAME = "Flylite";

	/**
	 * Human readable name of the database.
	 * @return name.
	 */
	public abstract String getName();

	@Override
	public String toString() {
		return getName();
	}

	/**
	 * The JDBC type used for null values.
	 * @return {@link java.sql.Types} constant.
	 */
	public abstract int getNullType();

	/**
	 * Whether the database supports read only transactions.
	 * @return true by default.
	 */
	public boolean supportsReadOnlyTransactions() {
		return true;
	}

	/**
	 * Whether this database type handles the JDBC url.
	 * @param url jdbc url.
	 * @return true if handled.
	 */
	public abstract boolean handlesJDBCUrl(String url);

	/**
	 * Pattern used to find credentials in a JDBC url so that they can be redacted.
	 * @return pattern whose first group is the password.
	 */
	public Pattern getJDBCCredentialsPattern() {
		return DEFAULT_JDBC_CREDENTIALS_PATTERN;
	}

	static Pattern defaultJDBCCredentialsPattern() {
		return DEFAULT_JDBC_CREDENTIALS_PATTERN;
	}

	/**
	 * The default JDBC driver class name for the url.
	 * @param url jdbc url.
	 * @return driver class name.
	 */
	public abstract String getDriverClass(String url);

	/**
	 * Whether this type handles the connected database.
	 * @param databaseProductName product name from jdbc metadata.
	 * @return true if handled.
	 */
	public abstract boolean handlesDatabaseProductName(String databaseProductName);

	/**
	 * Creates the database abstraction for the given connection factory.
	 * @param configuration config.
	 * @param printInfo whether to log connection info.
	 * @param jdbcConnectionFactory connection factory.
	 * @return database.
	 */
	public Database<?> createDatabase(Configuration configuration, boolean printInfo,
			JdbcConnectionFactory jdbcConnectionFactory) {
		if (printInfo) {
			LOG.info("Database: " + jdbcConnectionFactory.getJdbcUrl() + " (" + jdbcConnectionFactory.getProductName()
					+ ")");
			LOG.debug("Driver  : " + jdbcConnectionFactory.getDriverInfo());
		}
		return createDatabase(configuration, jdbcConnectionFactory);
	}

	/**
	 * Creates the database abstraction for the given connection factory.
	 * @param configuration config.
	 * @param jdbcConnectionFactory connection factory.
	 * @return database.
	 */
	protected abstract Database<?> createDatabase(Configuration configuration,
			JdbcConnectionFactory jdbcConnectionFactory);

	/**
	 * Creates the SQL parser for this database.
	 * @param configuration config.
	 * @param parsingContext parsing context.
	 * @return parser.
	 */
	public abstract Parser createParser(Configuration configuration, ParsingContext parsingContext);

	/**
	 * Creates a factory of SQL scripts parsed with this database's parser.
	 * @param configuration config.
	 * @param parsingContext parsing context.
	 * @return factory.
	 */
	public SqlScriptFactory createSqlScriptFactory(Configuration configuration, ParsingContext parsingContext) {
		return (resource, mixed, resourceProvider) -> new ParserSqlScript(createParser(configuration, parsingContext),
				resource, SqlScriptMetadata.getMetadataResource(resourceProvider, resource), mixed);
	}

	/**
	 * Creates a factory of SQL script executors.
	 * @return factory.
	 */
	public SqlScriptExecutorFactory createSqlScriptExecutorFactory() {
		return connection -> new DefaultSqlScriptExecutor(new JdbcTemplate(connection, this));
	}

	/**
	 * Creates a transactional execution template.
	 * @param connection jdbc connection.
	 * @param rollbackOnException whether to roll back on failure.
	 * @return template.
	 */
	public ExecutionTemplate createTransactionalExecutionTemplate(Connection connection, boolean rollbackOnException) {
		return new TransactionalExecutionTemplate(connection, rollbackOnException);
	}

	/**
	 * Sets driver properties that are always set for this database. User supplied jdbc
	 * properties override these.
	 * @param props driver properties.
	 */
	public void setDefaultConnectionProps(Properties props) {
	}

	/**
	 * Runs {@code SELECT version()}.
	 * @param connection jdbc connection.
	 * @return version output or empty string.
	 */
	public static String getSelectVersionOutput(Connection connection) {
		try (PreparedStatement statement = connection.prepareStatement("SELECT version()");
				ResultSet resultSet = statement.executeQuery()) {
			if (resultSet.next()) {
				@Nullable String result = resultSet.getString(1);
				return result == null ? "" : result;
			}
			return "";
		}
		catch (SQLException e) {
			return "";
		}
	}

}
