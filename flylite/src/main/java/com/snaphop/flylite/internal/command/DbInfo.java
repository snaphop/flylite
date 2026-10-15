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
package com.snaphop.flylite.internal.command;

import java.util.List;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.MigrationInfoService;
import com.snaphop.flylite.api.callback.Event;
import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.internal.callback.CallbackExecutor;
import com.snaphop.flylite.internal.database.base.Database;
import com.snaphop.flylite.internal.database.base.Schema;
import com.snaphop.flylite.internal.info.MigrationInfoServiceImpl;
import com.snaphop.flylite.internal.resolver.CompositeMigrationResolver;
import com.snaphop.flylite.internal.schemahistory.SchemaHistory;
import com.snaphop.flylite.internal.util.ValidatePatternUtils;

public class DbInfo {

	private final CompositeMigrationResolver migrationResolver;

	private final SchemaHistory schemaHistory;

	private final Configuration configuration;

	private final Database<?> database;

	private final CallbackExecutor callbackExecutor;

	private final List<Schema<?, ?>> schemas;

	public MigrationInfoService info() {
		callbackExecutor.onEvent(Event.BEFORE_INFO);
		MigrationInfoServiceImpl migrationInfoService;
		try {
			migrationInfoService = new MigrationInfoServiceImpl(migrationResolver, schemaHistory, database,
					configuration, configuration.getTarget(), configuration.isOutOfOrder(),
					ValidatePatternUtils.getIgnoreAllPattern());
			migrationInfoService.refresh();
			migrationInfoService.setAllSchemasEmpty(schemas);
		}
		catch (FlyliteException e) {
			callbackExecutor.onEvent(Event.AFTER_INFO_ERROR);
			throw e;
		}
		callbackExecutor.onEvent(Event.AFTER_INFO);
		return migrationInfoService;
	}

	public DbInfo(final CompositeMigrationResolver migrationResolver, final SchemaHistory schemaHistory,
			final Configuration configuration, final Database<?> database, final CallbackExecutor callbackExecutor,
			final List<Schema<?, ?>> schemas) {
		this.migrationResolver = migrationResolver;
		this.schemaHistory = schemaHistory;
		this.configuration = configuration;
		this.database = database;
		this.callbackExecutor = callbackExecutor;
		this.schemas = schemas;
	}

}
