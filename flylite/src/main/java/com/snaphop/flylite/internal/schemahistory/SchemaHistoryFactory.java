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
package com.snaphop.flylite.internal.schemahistory;

import java.util.ArrayList;
import java.util.List;

import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.internal.database.base.Database;
import com.snaphop.flylite.internal.database.base.Schema;
import com.snaphop.flylite.internal.logging.Log;
import com.snaphop.flylite.internal.sqlscript.SqlScriptExecutorFactory;
import com.snaphop.flylite.internal.sqlscript.SqlScriptFactory;

public final class SchemaHistoryFactory {

	private static final Log LOG = Log.of(SchemaHistoryFactory.class);

	private SchemaHistoryFactory() {
	}

	/**
	 * The schemas Flylite manages.
	 *
	 * @param defaultSchema the schema containing the schema history table.
	 * @param all all managed schemas, the default schema included.
	 */
	public record Schemas(Schema<?, ?> defaultSchema, List<Schema<?, ?>> all) {
	}

	public static SchemaHistory getSchemaHistory(Configuration configuration,
			SqlScriptExecutorFactory sqlScriptExecutorFactory, SqlScriptFactory sqlScriptFactory, Database<?> database,
			Schema<?, ?> schema) {
		return new JdbcTableSchemaHistory(sqlScriptExecutorFactory, sqlScriptFactory, database,
				schema.getTable(configuration.getTable()));
	}

	public static Schemas prepareSchemas(Configuration configuration, Database<?> database) {
		List<String> schemaNames = configuration.getSchemas();
		String defaultSchemaName = configuration.getDefaultSchema();

		LOG.debug("Schemas: " + String.join(",", schemaNames));
		LOG.debug("Default schema: " + defaultSchemaName);

		List<Schema<?, ?>> schemas = new ArrayList<>();
		for (String schemaName : schemaNames) {
			schemas.add(database.getMainConnection().getSchema(schemaName));
		}

		if (defaultSchemaName == null) {
			if (schemaNames.isEmpty()) {
				Schema<?, ?> currentSchema = database.getMainConnection().getCurrentSchema();
				if (currentSchema == null) {
					throw new FlyliteException(
							"Unable to determine schema for the schema history table. Set a default schema for the connection or specify one using the 'defaultSchema' property");
				}
				defaultSchemaName = currentSchema.getName();
			}
			else {
				defaultSchemaName = schemaNames.get(0);
			}
		}

		Schema<?, ?> defaultSchema = database.getMainConnection().getSchema(defaultSchemaName);
		if (!schemas.contains(defaultSchema)) {
			schemas.add(0, defaultSchema);
		}

		return new Schemas(defaultSchema, List.copyOf(schemas));
	}

}
