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
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.internal.database.postgresql.PostgreSQLDatabaseType;
import com.snaphop.flylite.internal.database.sqlite.SQLiteDatabaseType;
import com.snaphop.flylite.internal.jdbc.JdbcUtils;

/**
 * Finds the {@link DatabaseType} for a JDBC url or connection.
 */
public final class DatabaseTypeRegister {

	private static final List<DatabaseType> DATABASE_TYPES = List.of(new PostgreSQLDatabaseType(),
			new SQLiteDatabaseType());

	private DatabaseTypeRegister() {
	}

	/**
	 * Finds the database type for a JDBC url.
	 * @param url jdbc url.
	 * @return database type.
	 * @throws FlyliteException if the url is not supported.
	 */
	public static DatabaseType getDatabaseTypeForUrl(String url) {
		for (DatabaseType type : DATABASE_TYPES) {
			if (type.handlesJDBCUrl(url)) {
				return type;
			}
		}
		throw new FlyliteException("No database found to handle " + redactJdbcUrl(url));
	}

	/**
	 * Removes the password from a JDBC url.
	 * @param url jdbc url.
	 * @return redacted url.
	 */
	public static String redactJdbcUrl(String url) {
		for (DatabaseType type : DATABASE_TYPES) {
			if (type.handlesJDBCUrl(url)) {
				return redactJdbcUrl(url, type.getJDBCCredentialsPattern());
			}
		}
		return redactJdbcUrl(url, DatabaseType.defaultJDBCCredentialsPattern());
	}

	private static String redactJdbcUrl(String url, Pattern pattern) {
		Matcher matcher = pattern.matcher(url);
		if (matcher.find()) {
			String password = matcher.group(1);
			if (password != null && !password.isEmpty()) {
				return url.replace(password, "********");
			}
		}
		return url;
	}

	/**
	 * Finds the database type of the connected database.
	 * @param connection jdbc connection.
	 * @return database type.
	 * @throws FlyliteException if the database is not supported.
	 */
	public static DatabaseType getDatabaseTypeForConnection(Connection connection) {
		DatabaseMetaData databaseMetaData = JdbcUtils.getDatabaseMetaData(connection);
		String databaseProductName = JdbcUtils.getDatabaseProductName(databaseMetaData);
		for (DatabaseType type : DATABASE_TYPES) {
			if (type.handlesDatabaseProductName(databaseProductName)) {
				return type;
			}
		}
		throw new FlyliteException("Unsupported Database: " + databaseProductName);
	}

}
