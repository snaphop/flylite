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
package com.snaphop.flylite.internal.database.sqlite;

import java.sql.Types;

import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.internal.database.DatabaseType;
import com.snaphop.flylite.internal.database.base.Database;
import com.snaphop.flylite.internal.jdbc.JdbcConnectionFactory;
import com.snaphop.flylite.internal.parser.Parser;
import com.snaphop.flylite.internal.parser.ParsingContext;

public final class SQLiteDatabaseType extends DatabaseType {

	@Override
	public String getName() {
		return "SQLite";
	}

	@Override
	public int getNullType() {
		return Types.VARCHAR;
	}

	@Override
	public boolean supportsReadOnlyTransactions() {
		return false;
	}

	@Override
	public boolean handlesJDBCUrl(String url) {
		return url.startsWith("jdbc:sqlite:") || url.startsWith("jdbc:p6spy:sqlite:");
	}

	@Override
	public String getDriverClass(String url) {
		if (url.startsWith("jdbc:p6spy:sqlite:")) {
			return "com.p6spy.engine.spy.P6SpyDriver";
		}
		return "org.sqlite.JDBC";
	}

	@Override
	public boolean handlesDatabaseProductName(String databaseProductName) {
		return databaseProductName.startsWith("SQLite");
	}

	@Override
	protected Database<?> createDatabase(Configuration configuration, JdbcConnectionFactory jdbcConnectionFactory) {
		return new SQLiteDatabase(configuration, jdbcConnectionFactory);
	}

	@Override
	public Parser createParser(Configuration configuration, ParsingContext parsingContext) {
		return new SQLiteParser(configuration, parsingContext);
	}

}
