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
package com.snaphop.flylite.internal.parser;

import com.snaphop.flylite.internal.logging.Log;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.internal.database.base.Database;
import com.snaphop.flylite.internal.database.base.Schema;
import com.snaphop.flylite.internal.resource.ResourceName;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParsingContext {

	private static final Log LOG = Log.of(ParsingContext.class);

	private static final String DEFAULT_SCHEMA_PLACEHOLDER = "defaultSchema";

	private static final String USER_PLACEHOLDER = "user";

	private static final String DATABASE_PLACEHOLDER = "database";

	private static final String TIMESTAMP_PLACEHOLDER = "timestamp";

	private static final String FILENAME_PLACEHOLDER = "filename";

	private static final String WORKING_DIRECTORY_PLACEHOLDER = "workingDirectory";

	private static final String TABLE_PLACEHOLDER = "table";

	private final Map<String, String> placeholders = new HashMap<>();

	private Database<?> database;

	private String generateName(String name, Configuration configuration) {
		return "flyway" + configuration.getPlaceholderSeparator() + name;
	}

	public void populate(Database<?> database, Configuration configuration) {
		setDatabase(database);
		String defaultSchemaName = configuration.getDefaultSchema();
		List<String> schemaNames = configuration.getSchemas();
		Schema<?, ?> currentSchema = getCurrentSchema(database);
		String catalog = database.getCatalog();
		String currentUser = getCurrentUser(database);
		// cf. Flylite.prepareSchemas()
		if (defaultSchemaName == null) {
			if (!schemaNames.isEmpty()) {
				defaultSchemaName = schemaNames.get(0);
			}
			else {
				defaultSchemaName = currentSchema.getName();
			}
		}
		if (defaultSchemaName != null) {
			placeholders.put(generateName(DEFAULT_SCHEMA_PLACEHOLDER, configuration), defaultSchemaName);
		}
		if (catalog != null) {
			placeholders.put(generateName(DATABASE_PLACEHOLDER, configuration), catalog);
		}
		placeholders.put(generateName(USER_PLACEHOLDER, configuration), currentUser);
		placeholders.put(generateName(TIMESTAMP_PLACEHOLDER, configuration),
				new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
		placeholders.put(generateName(WORKING_DIRECTORY_PLACEHOLDER, configuration), System.getProperty("user.dir"));
		placeholders.put(generateName(TABLE_PLACEHOLDER, configuration), configuration.getTable());
	}

	public void updateFilenamePlaceholder(ResourceName resourceName, Configuration configuration) {
		String filenamePlaceholder = generateName(FILENAME_PLACEHOLDER, configuration);
		if (resourceName.isValid()) {
			placeholders.put(filenamePlaceholder, resourceName.getFilename());
		}
		else {
			placeholders.remove(filenamePlaceholder);
		}
	}

	private Schema<?, ?> getCurrentSchema(Database<?> database) {
		try {
			return database.getMainConnection().getCurrentSchema();
		}
		catch (FlyliteException e) {
			LOG.debug("Could not get schema for " + DEFAULT_SCHEMA_PLACEHOLDER + " placeholder.");
			return null;
		}
	}

	private String getCurrentUser(Database<?> database) {
		try {
			return database.getCurrentUser();
		}
		catch (FlyliteException e) {
			LOG.debug("Could not get user for " + USER_PLACEHOLDER + " placeholder.");
			return null;
		}
	}

	public Map<String, String> getPlaceholders() {
		return this.placeholders;
	}

	public Database<?> getDatabase() {
		return this.database;
	}

	public void setDatabase(final Database<?> database) {
		this.database = database;
	}

}
