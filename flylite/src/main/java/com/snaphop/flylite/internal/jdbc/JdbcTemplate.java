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
package com.snaphop.flylite.internal.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.internal.database.DatabaseType;
import com.snaphop.flylite.internal.database.DatabaseTypeRegister;

/**
 * Collection of utility methods for querying the DB. Inspired by Spring's JdbcTemplate.
 */
public class JdbcTemplate {

	protected final Connection connection;

	/**
	 * The type to assign to a null value.
	 */
	protected final int nullType;

	public JdbcTemplate(Connection connection) {
		this(connection, DatabaseTypeRegister.getDatabaseTypeForConnection(connection));
	}

	public JdbcTemplate(Connection connection, DatabaseType databaseType) {
		this.connection = connection;
		this.nullType = databaseType.getNullType();
	}

	public Connection getConnection() {
		return connection;
	}

	public List<Map<String, @Nullable String>> queryForList(String query, @Nullable Object... params)
			throws SQLException {
		return query(query, resultSet -> {
			Map<String, @Nullable String> rowMap = new LinkedHashMap<>();
			ResultSetMetaData metaData = resultSet.getMetaData();
			for (int i = 1; i <= metaData.getColumnCount(); i++) {
				rowMap.put(metaData.getColumnLabel(i), resultSet.getString(i));
			}
			return rowMap;
		}, params);
	}

	/**
	 * Queries a single non null string column.
	 * @param query query.
	 * @param params parameters.
	 * @return the values.
	 * @throws SQLException if the query fails.
	 */
	public List<String> queryForStringList(String query, String... params) throws SQLException {
		return query(query, resultSet -> {
			String value = resultSet.getString(1);
			if (value == null) {
				throw new SQLException("Unexpected null value for query: " + query);
			}
			return value;
		}, (Object[]) params);
	}

	public int queryForInt(String query, String... params) throws SQLException {
		try (PreparedStatement statement = prepareStatement(query, params);
				ResultSet resultSet = statement.executeQuery()) {
			resultSet.next();
			return resultSet.getInt(1);
		}
	}

	public boolean queryForBoolean(String query, String... params) throws SQLException {
		try (PreparedStatement statement = prepareStatement(query, params);
				ResultSet resultSet = statement.executeQuery()) {
			resultSet.next();
			return resultSet.getBoolean(1);
		}
	}

	/**
	 * Queries the first column of the first row.
	 * @param query query.
	 * @param params parameters.
	 * @return the value or {@code null} if there are no rows or the value is null.
	 * @throws SQLException if the query fails.
	 */
	public @Nullable String queryForString(String query, String... params) throws SQLException {
		try (PreparedStatement statement = prepareStatement(query, params);
				ResultSet resultSet = statement.executeQuery()) {
			if (resultSet.next()) {
				return resultSet.getString(1);
			}
			return null;
		}
	}

	/**
	 * Queries the first column of the first row which must not be null.
	 * @param query query.
	 * @param params parameters.
	 * @return the value.
	 * @throws SQLException if the query fails or returns no value.
	 */
	public String queryForNonNullString(String query, String... params) throws SQLException {
		String value = queryForString(query, params);
		if (value == null) {
			throw new SQLException("No value returned by: " + query);
		}
		return value;
	}

	/**
	 * Executes this sql statement using a PreparedStatement.
	 * @param sql The statement to execute.
	 * @param params The statement parameters.
	 * @throws SQLException when the execution failed.
	 */
	public void execute(String sql, @Nullable Object... params) throws SQLException {
		try (PreparedStatement statement = prepareStatement(sql, params)) {
			statement.execute();
		}
	}

	/**
	 * Executes this sql statement using an ordinary Statement.
	 * @param sql The statement to execute.
	 * @return the results of the execution.
	 */
	public Results executeStatement(String sql) {
		Results results = new Results();
		try (Statement statement = connection.createStatement()) {
			statement.setEscapeProcessing(false);
			boolean hasResults = statement.execute(sql);
			extractResults(results, statement, sql, hasResults);
			extractWarnings(results, statement);
		}
		catch (final SQLException e) {
			results.setException(e);
		}
		return results;
	}

	private void extractWarnings(Results results, Statement statement) throws SQLException {
		SQLWarning warning = statement.getWarnings();
		while (warning != null) {
			int code = warning.getErrorCode();
			String state = warning.getSQLState();
			String message = warning.getMessage();
			results.addWarning(new WarningImpl(code, state == null ? "" : state, message == null ? "" : message));
			warning = warning.getNextWarning();
		}
	}

	private void extractResults(Results results, Statement statement, String sql, boolean hasResults)
			throws SQLException {
		// retrieve all results to ensure all errors are detected
		int updateCount = -1;
		while (hasResults || (updateCount = statement.getUpdateCount()) != -1) {
			List<String> columns = null;
			List<List<@Nullable String>> data = null;
			if (hasResults) {
				try (ResultSet resultSet = statement.getResultSet()) {
					columns = new ArrayList<>();
					ResultSetMetaData metadata = resultSet.getMetaData();
					int columnCount = metadata.getColumnCount();
					for (int i = 1; i <= columnCount; i++) {
						columns.add(metadata.getColumnName(i));
					}
					data = new ArrayList<>();
					while (resultSet.next()) {
						List<@Nullable String> row = new ArrayList<>();
						for (int i = 1; i <= columnCount; i++) {
							row.add(resultSet.getString(i));
						}
						data.add(row);
					}
				}
			}
			results.addResult(new Result(updateCount, columns, data, sql));
			hasResults = statement.getMoreResults();
		}
	}

	/**
	 * Executes this update sql statement.
	 * @param sql The statement to execute.
	 * @param params The statement parameters.
	 * @throws SQLException when the execution failed.
	 */
	public void update(String sql, @Nullable Object... params) throws SQLException {
		try (PreparedStatement statement = prepareStatement(sql, params)) {
			statement.executeUpdate();
		}
	}

	/**
	 * Creates a new prepared statement for this sql with these params.
	 * @param sql The sql to execute.
	 * @param params The params.
	 * @return The new prepared statement.
	 * @throws SQLException when the statement could not be prepared.
	 */
	protected PreparedStatement prepareStatement(String sql, @Nullable Object[] params) throws SQLException {
		PreparedStatement statement = connection.prepareStatement(sql);
		for (int i = 0; i < params.length; i++) {
			Object param = params[i];
			if (param == null || param instanceof JdbcNullTypes) {
				statement.setNull(i + 1, nullType);
			}
			else if (param instanceof Integer integer) {
				statement.setInt(i + 1, integer);
			}
			else if (param instanceof Boolean bool) {
				statement.setBoolean(i + 1, bool);
			}
			else if (param instanceof String string) {
				statement.setString(i + 1, string);
			}
			else {
				statement.close();
				throw new FlyliteException("Unhandled object of type '" + param.getClass().getName() + "'.");
			}
		}
		return statement;
	}

	/**
	 * Executes this query and map the results using this row mapper.
	 * @param sql The query to execute.
	 * @param rowMapper The row mapper to use.
	 * @param params The statement parameters.
	 * @param <T> The type of the result objects.
	 * @return The list of results.
	 * @throws SQLException when the query failed to execute.
	 */
	public <T> List<T> query(String sql, RowMapper<T> rowMapper, @Nullable Object... params) throws SQLException {
		try (PreparedStatement statement = prepareStatement(sql, params);
				ResultSet resultSet = statement.executeQuery()) {
			List<T> results = new ArrayList<>();
			while (resultSet.next()) {
				results.add(rowMapper.mapRow(resultSet));
			}
			return results;
		}
	}

}
