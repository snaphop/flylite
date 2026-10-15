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
package com.snaphop.flylite.internal.callback;

import com.snaphop.flylite.internal.logging.Log;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.ResourceProvider;
import com.snaphop.flylite.api.callback.Callback;
import com.snaphop.flylite.api.callback.Context;
import com.snaphop.flylite.api.callback.Event;
import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.api.resource.LoadableResource;
import com.snaphop.flylite.internal.resource.ResourceName;
import com.snaphop.flylite.internal.resource.ResourceNameParser;
import com.snaphop.flylite.internal.sqlscript.SqlScript;
import com.snaphop.flylite.internal.sqlscript.SqlScriptExecutorFactory;
import com.snaphop.flylite.internal.sqlscript.SqlScriptFactory;
import java.util.*;

/**
 * Callback factory, looking for SQL scripts (named like on the callback methods) inside
 * the configured locations.
 */
public class SqlScriptCallbackFactory {

	private static final Log LOG = Log.of(SqlScriptCallbackFactory.class);

	private final List<SqlScriptCallback> callbacks = new ArrayList<>();

	/**
	 * Creates a new instance.
	 * @param resourceProvider The resource provider.
	 * @param sqlScriptFactory The SQL statement factory.
	 * @param configuration The Flylite configuration.
	 */
	public SqlScriptCallbackFactory(ResourceProvider resourceProvider,
			SqlScriptExecutorFactory sqlScriptExecutorFactory, SqlScriptFactory sqlScriptFactory,
			Configuration configuration) {
		Map<String, SqlScript> callbacksFound = new HashMap<>();
		LOG.debug("Scanning for SQL callbacks ...");
		Collection<LoadableResource> resources = resourceProvider.getResources("",
				configuration.getSqlMigrationSuffixes());
		ResourceNameParser resourceNameParser = new ResourceNameParser(configuration);
		for (LoadableResource resource : resources) {
			ResourceName parsedName = resourceNameParser.parse(resource.getFilename());
			if (!parsedName.isValid()) {
				continue;
			}
			String name = parsedName.getFilenameWithoutSuffix();
			Event event = Event.fromId(parsedName.getPrefix());
			if (event != null) {
				SqlScript existing = callbacksFound.get(name);
				if (existing != null) {
					throw new FlyliteException("Found more than 1 SQL callback script called " + name + "!\n"
							+ "Offenders:\n" + "-> " + existing.getResource().getAbsolutePathOnDisk() + "\n" + "-> "
							+ resource.getAbsolutePathOnDisk());
				}
				SqlScript sqlScript = sqlScriptFactory.createSqlScript(resource, configuration.isMixed(),
						resourceProvider);
				callbacksFound.put(name, sqlScript);
				callbacks.add(
						new SqlScriptCallback(event, parsedName.getDescription(), sqlScriptExecutorFactory, sqlScript));
			}
		}
		Collections.sort(callbacks);
	}

	public List<Callback> getCallbacks() {
		return new ArrayList<>(callbacks);
	}

	private static class SqlScriptCallback implements Callback, Comparable<SqlScriptCallback> {

		private final Event event;

		private final String description;

		private final SqlScriptExecutorFactory sqlScriptExecutorFactory;

		private final SqlScript sqlScript;

		@Override
		public boolean supports(Event event, Context context) {
			return this.event == event;
		}

		@Override
		public boolean canHandleInTransaction(Event event, Context context) {
			return sqlScript.executeInTransaction();
		}

		@Override
		public void handle(Event event, Context context) {
			if (!sqlScript.shouldExecute()) {
				LOG.debug("Not executing SQL callback: " + event.getId()
						+ (description == null ? "" : " - " + description));
				return;
			}
			LOG.info("Executing SQL callback: " + event.getId() + (description == null ? "" : " - " + description)
					+ (sqlScript.executeInTransaction() ? "" : " [non-transactional]"));
			sqlScriptExecutorFactory.createSqlScriptExecutor(context.getConnection()).execute(sqlScript);
		}

		@Override
		public String getCallbackName() {
			return description;
		}

		@Override
		public int compareTo(SqlScriptCallback o) {
			int result = event.compareTo(o.event);
			if (result == 0) {
				if (description == null) {
					return -1;
				}
				if (o.description == null) {
					return 1;
				}
				result = description.compareTo(o.description);
			}
			return result;
		}

		private SqlScriptCallback(final Event event, final String description,
				final SqlScriptExecutorFactory sqlScriptExecutorFactory, final SqlScript sqlScript) {
			this.event = event;
			this.description = description;
			this.sqlScriptExecutorFactory = sqlScriptExecutorFactory;
			this.sqlScript = sqlScript;
		}

	}

}
