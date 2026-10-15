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

import com.snaphop.flylite.internal.logging.Log;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.internal.exception.FlyliteSqlException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Callable;

/**
 * Spring-like template for executing transactions.
 */
public class TransactionalExecutionTemplate implements ExecutionTemplate {

	private static final Log LOG = Log.of(TransactionalExecutionTemplate.class);

	/**
	 * The connection to the database
	 */
	private final Connection connection;

	/**
	 * Whether to roll back the transaction when an exception is thrown.
	 */
	private final boolean rollbackOnException;

	/**
	 * Executes this callback within a transaction.
	 * @param callback The callback to execute.
	 * @return The result of the transaction code.
	 */
	@Override
	public <T> T execute(Callable<T> callback) {
		boolean oldAutocommit = true;
		try {
			oldAutocommit = connection.getAutoCommit();
			connection.setAutoCommit(false);
			T result = callback.call();
			connection.commit();
			return result;
		}
		catch (Exception e) {
			RuntimeException rethrow;
			if (e instanceof SQLException) {
				rethrow = new FlyliteSqlException("Unable to commit transaction", (SQLException) e);
			}
			else if (e instanceof RuntimeException) {
				rethrow = (RuntimeException) e;
			}
			else {
				rethrow = new FlyliteException(e);
			}
			if (rollbackOnException) {
				try {
					LOG.debug("Rolling back transaction...");
					connection.rollback();
					LOG.debug("Transaction rolled back");
				}
				catch (SQLException se) {
					LOG.error("Unable to rollback transaction", se);
				}
			}
			else {
				try {
					connection.commit();
				}
				catch (SQLException se) {
					LOG.error("Unable to commit transaction", se);
				}
			}
			throw rethrow;
		}
		finally {
			try {
				connection.setAutoCommit(oldAutocommit);
			}
			catch (SQLException e) {
				LOG.error("Unable to restore autocommit to original value for connection", e);
			}
		}
	}

	/**
	 * Creates a new {@code TransactionalExecutionTemplate} instance.
	 * @param connection The connection to the database
	 * @param rollbackOnException Whether to roll back the transaction when an exception
	 * is thrown.
	 */
	public TransactionalExecutionTemplate(final Connection connection, final boolean rollbackOnException) {
		this.connection = connection;
		this.rollbackOnException = rollbackOnException;
	}

}
