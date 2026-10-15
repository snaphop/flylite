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

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.internal.logging.Log;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.internal.database.DatabaseTypeRegister;
import com.snaphop.flylite.internal.exception.FlyliteSqlException;
import com.snaphop.flylite.internal.strategy.BackoffStrategy;
import com.snaphop.flylite.internal.util.ExceptionUtils;
import javax.sql.DataSource;
import java.sql.*;

public class JdbcUtils {

	private static final Log LOG = Log.of(JdbcUtils.class);

	/**
	 * Opens a new connection from this DataSource.
	 * @param dataSource The DataSource to obtain the connection from.
	 * @param connectRetries The maximum number of retries when attempting to connect to
	 * the database.
	 * @param connectRetriesInterval The maximum time between retries in seconds.
	 * @return The new connection.
	 * @throws FlyliteException when the connection could not be opened.
	 */
	public static Connection openConnection(DataSource dataSource, int connectRetries, int connectRetriesInterval)
			throws FlyliteException {
		BackoffStrategy backoffStrategy = new BackoffStrategy(1, 2, connectRetriesInterval);
		int retries = 0;
		while (true) {
			try {
				return dataSource.getConnection();
			}
			catch (SQLException e) {
				if (++retries > connectRetries) {
					throw new FlyliteSqlException("Unable to obtain connection from database"
							+ getDataSourceInfo(dataSource) + ": " + e.getMessage(), e);
				}
				Throwable rootCause = ExceptionUtils.getRootCause(e);
				String message = "Connection error: " + e.getMessage();
				if (rootCause != null && !rootCause.equals(e) && rootCause.getMessage() != null) {
					message += "\n(Caused by " + rootCause.getMessage() + ")";
				}
				LOG.warn(message + "\nRetrying in " + backoffStrategy.peek() + " sec...");
				try {
					Thread.sleep(backoffStrategy.next() * 1000L);
				}
				catch (InterruptedException e1) {
					throw new FlyliteSqlException("Unable to obtain connection from database"
							+ getDataSourceInfo(dataSource) + ": " + e.getMessage(), e);
				}
			}
		}
	}

	private static String getDataSourceInfo(DataSource dataSource) {
		if (dataSource instanceof DriverDataSource driverDataSource) {
			return " (" + DatabaseTypeRegister.redactJdbcUrl(driverDataSource.getUrl()) + ")";
		}
		return "";
	}

	/**
	 * Safely closes this Connection. This method never fails.
	 */
	public static void closeConnection(@Nullable Connection connection) {
		if (connection == null) {
			return;
		}
		try {
			if (!connection.isClosed()) {
				connection.close();
			}
		}
		catch (Exception e) {
			LOG.error("Error while closing database Connection: " + e.getMessage(), e);
		}
	}

	/**
	 * Safely closes this Statement. This method never fails.
	 */
	public static void closeStatement(@Nullable Statement statement) {
		if (statement == null) {
			return;
		}
		try {
			statement.close();
		}
		catch (SQLException e) {
			LOG.error("Error while closing JDBC Statement", e);
		}
	}

	/**
	 * Safely closes this ResultSet. This method never fails.
	 */
	public static void closeResultSet(@Nullable ResultSet resultSet) {
		if (resultSet == null) {
			return;
		}
		try {
			resultSet.close();
		}
		catch (SQLException e) {
			LOG.error("Error while closing JDBC ResultSet", e);
		}
	}

	public static DatabaseMetaData getDatabaseMetaData(Connection connection) {
		DatabaseMetaData databaseMetaData;
		try {
			databaseMetaData = connection.getMetaData();
		}
		catch (SQLException e) {
			throw new FlyliteSqlException("Unable to read database connection metadata: " + e.getMessage(), e);
		}
		if (databaseMetaData == null) {
			throw new FlyliteException("Unable to read database connection metadata while it is null!");
		}
		return databaseMetaData;
	}

	/**
	 * Returns the name of the database product. Example: Oracle, MySQL, ...
	 * @return The name of the database product. Example: Oracle, MySQL, ...
	 */
	public static String getDatabaseProductName(DatabaseMetaData databaseMetaData) {
		try {
			String databaseProductName = databaseMetaData.getDatabaseProductName();
			if (databaseProductName == null) {
				throw new FlyliteException("Unable to determine database. Product name is null.");
			}
			return databaseProductName + " " + databaseMetaData.getDatabaseMajorVersion() + "."
					+ databaseMetaData.getDatabaseMinorVersion();
		}
		catch (SQLException e) {
			throw new FlyliteSqlException("Error while determining database product name", e);
		}
	}

	/**
	 * Returns the version of the database product. Example: MariaDB 10.3, ...
	 * @return The version of the database product. Example: MariaDB 10.3, ...
	 */

	private JdbcUtils() {
	}

}
