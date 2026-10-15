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

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.internal.logging.Log;
import com.snaphop.flylite.api.callback.Event;
import com.snaphop.flylite.internal.jdbc.Warning;
import com.snaphop.flylite.internal.jdbc.JdbcTemplate;
import com.snaphop.flylite.internal.jdbc.Result;
import com.snaphop.flylite.internal.jdbc.Results;
import com.snaphop.flylite.internal.util.AsciiTable;
import java.sql.SQLException;
import java.util.List;

public class DefaultSqlScriptExecutor implements SqlScriptExecutor {

	private static final Log LOG = Log.of(DefaultSqlScriptExecutor.class);

	protected final JdbcTemplate jdbcTemplate;

	public DefaultSqlScriptExecutor(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void execute(SqlScript sqlScript) {
		try (SqlStatementIterator sqlStatementIterator = sqlScript.getSqlStatements()) {
			while (sqlStatementIterator.hasNext()) {
				SqlStatement sqlStatement = sqlStatementIterator.next();
				executeStatement(jdbcTemplate, sqlScript, sqlStatement);
			}
		}
	}

	protected void logStatementExecution(SqlStatement sqlStatement) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("Executing " + "SQL: " + sqlStatement.getSql());
		}
	}

	protected void executeStatement(JdbcTemplate jdbcTemplate, SqlScript sqlScript, SqlStatement sqlStatement) {
		logStatementExecution(sqlStatement);
		Results results = sqlStatement.execute(jdbcTemplate);
		printWarnings(results);
		SQLException exception = results.getException();
		if (exception != null) {
			throw new FlyliteSqlScriptException(sqlScript.getResource(), sqlStatement, exception);
		}
		handleResults(results);
	}

	protected void handleResults(Results results) {
		for (Result result : results.getResults()) {
			long updateCount = result.updateCount();
			if (updateCount != -1) {
				handleUpdateCount(updateCount);
			}
			outputQueryResult(result);
		}
	}

	protected void outputQueryResult(Result result) {
		List<String> columns = result.columns();
		List<List<@Nullable String>> data = result.data();
		if (columns != null && data != null && !columns.isEmpty()) {
			LOG.info(new AsciiTable(columns, data, true, "", "No rows returned").render());
		}
	}

	private void handleUpdateCount(long updateCount) {
		LOG.debug(updateCount + (updateCount == 1 ? " row" : " rows") + " affected");
	}

	private void printWarnings(Results results) {
		for (Warning warning : results.getWarnings()) {
			if ("00000".equals(warning.getState())) {
				LOG.info("DB: " + warning.getMessage());
			}
			else {
				LOG.warn("DB: " + warning.getMessage() + " (SQL State: " + warning.getState() + " - Error Code: "
						+ warning.getCode() + ")");
			}
		}
	}

}
