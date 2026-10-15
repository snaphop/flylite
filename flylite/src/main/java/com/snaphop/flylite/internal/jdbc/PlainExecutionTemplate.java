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
import java.sql.SQLException;
import java.util.concurrent.Callable;

public class PlainExecutionTemplate implements ExecutionTemplate {

	private static final Log LOG = Log.of(PlainExecutionTemplate.class);

	private final boolean skipErrorLog;

	public PlainExecutionTemplate() {
		this.skipErrorLog = false;
	}

	public PlainExecutionTemplate(boolean skipErrorLog) {
		this.skipErrorLog = skipErrorLog;
	}

	@Override
	public <T> T execute(Callable<T> callback) {
		try {
			LOG.debug("Performing operation in non-transactional context.");
			return callback.call();
		}
		catch (Exception e) {
			if (!skipErrorLog) {
				LOG.error(
						"Failed to execute operation in non-transactional context. Please restore backups and roll back database and code!");
			}
			if (e instanceof SQLException) {
				throw new FlyliteSqlException("Failed to execute operation.", (SQLException) e);
			}
			if (e instanceof RuntimeException) {
				throw (RuntimeException) e;
			}
			throw new FlyliteException(e);
		}
	}

}
