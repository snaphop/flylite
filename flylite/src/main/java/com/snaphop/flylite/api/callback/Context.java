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
package com.snaphop.flylite.api.callback;

import java.sql.Connection;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.api.MigrationInfo;
import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.api.output.OperationResult;

/**
 * The context relevant to an event.
 */
public interface Context {

	/**
	 * The configuration currently in use.
	 * @return configuration.
	 */
	Configuration getConfiguration();

	/**
	 * The JDBC connection being used. Transactions are managed by Flylite. When the
	 * context is passed to the {@link Callback#handle(Event, Context)} method, a
	 * transaction will already have been started if required and will be automatically
	 * committed or rolled back afterwards.
	 * @return connection.
	 */
	Connection getConnection();

	/**
	 * The info about the migration being handled. Only relevant for the BEFORE_EACH_* and
	 * AFTER_EACH_* events.
	 * @return info or {@code null} in all other cases.
	 */
	@Nullable MigrationInfo getMigrationInfo();

	/**
	 * The result of the finished operation. Only relevant for the
	 * AFTER_*_OPERATION_FINISH events.
	 * @return result or {@code null} in all other cases.
	 */
	@Nullable OperationResult getOperationResult();

}
