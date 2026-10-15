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
package com.snaphop.flylite.internal.command.clean;

import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.callback.Event;
import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.api.output.CleanResult;
import com.snaphop.flylite.internal.command.CommandResultFactory;
import com.snaphop.flylite.internal.callback.CallbackExecutor;
import com.snaphop.flylite.internal.database.base.Connection;
import com.snaphop.flylite.internal.database.base.Database;
import com.snaphop.flylite.internal.database.base.Schema;
import com.snaphop.flylite.internal.schemahistory.SchemaHistory;
import java.util.List;

public class DbClean {

	private final SchemaHistory schemaHistory;

	protected final Schema<?, ?> defaultSchema;

	protected final List<Schema<?, ?>> schemas;

	protected final Connection<?> connection;

	protected final Database<?> database;

	protected final CallbackExecutor callbackExecutor;

	protected final Configuration configuration;

	public DbClean(Database<?> database, SchemaHistory schemaHistory, Schema<?, ?> defaultSchema,
			List<Schema<?, ?>> schemas, CallbackExecutor callbackExecutor, Configuration configuration) {
		this.schemaHistory = schemaHistory;
		this.defaultSchema = defaultSchema;
		this.schemas = schemas;
		this.connection = database.getMainConnection();
		this.database = database;
		this.callbackExecutor = callbackExecutor;
		this.configuration = configuration;
	}

	public CleanResult clean() throws FlyliteException {
		if (configuration.isCleanDisabled()) {
			throw new FlyliteException(
					"Unable to execute clean as it has been disabled with the cleanDisabled configuration.");
		}
		callbackExecutor.onEvent(Event.BEFORE_CLEAN);
		CleanResult cleanResult = CommandResultFactory.createCleanResult(database.getCatalog());
		new CleanExecutor(connection, database, schemaHistory, callbackExecutor).clean(defaultSchema, schemas,
				cleanResult);
		callbackExecutor.onEvent(Event.AFTER_CLEAN);
		schemaHistory.clearCache();
		return cleanResult;
	}

}
