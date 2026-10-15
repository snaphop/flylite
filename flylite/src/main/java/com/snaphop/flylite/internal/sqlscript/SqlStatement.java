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
package com.snaphop.flylite.internal.sqlscript;

import com.snaphop.flylite.internal.jdbc.JdbcTemplate;
import com.snaphop.flylite.internal.jdbc.Results;

/**
 * A sql statement from a script that can be executed at once against a database.
 */
public interface SqlStatement {

	/**
	 * Returns the original line number where the statement was located in the script it
	 * @return The original line number where the statement was located in the script it
	 * came from.
	 */
	int getLineNumber();

	/**
	 * Returns the sql to send to the database.
	 * @return The sql to send to the database.
	 */
	String getSql();

	/**
	 * Returns the delimiter for the statement.
	 * @return The delimiter for the statement.
	 */
	String getDelimiter();

	/**
	 * Whether the execution should take place inside a transaction. Almost all
	 * implementation should return {@code true}. This however makes it possible to
	 * execute certain migrations outside a transaction. This is useful for databases like
	 * PostgreSQL and SQL Server where certain statement can only execute outside a
	 * transaction.
	 * @return {@code true} if a transaction should be used (highly recommended), or
	 * {@code false} if not.
	 */
	boolean canExecuteInTransaction();

	/**
	 * Executes this statement against the database.
	 * @param jdbcTemplate The jdbcTemplate to use to execute this script.
	 * @return the result of the execution.
	 */
	Results execute(JdbcTemplate jdbcTemplate

	);

}