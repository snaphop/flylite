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
package com.snaphop.flylite.internal.resolver.sql;

import com.snaphop.flylite.api.MigrationType;
import com.snaphop.flylite.api.ResourceProvider;
import com.snaphop.flylite.api.callback.Event;
import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.api.resolver.MigrationResolver;
import com.snaphop.flylite.api.resolver.ResolvedMigration;
import com.snaphop.flylite.api.resource.LoadableResource;
import com.snaphop.flylite.internal.parser.ParsingContext;
import com.snaphop.flylite.internal.parser.PlaceholderReplacingReader;
import com.snaphop.flylite.internal.resolver.ChecksumCalculator;
import com.snaphop.flylite.internal.resolver.ResolvedMigrationComparator;
import com.snaphop.flylite.internal.resolver.ResolvedMigrationImpl;
import com.snaphop.flylite.internal.resource.ResourceName;
import com.snaphop.flylite.internal.resource.ResourceNameParser;
import com.snaphop.flylite.internal.sqlscript.SqlScript;
import com.snaphop.flylite.internal.sqlscript.SqlScriptExecutorFactory;
import com.snaphop.flylite.internal.sqlscript.SqlScriptFactory;
import java.io.Reader;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Migration resolver for SQL files on the classpath. The SQL files must have names like
 * V1__Description.sql, V1_1__Description.sql, or R__description.sql.
 */
public class SqlMigrationResolver implements MigrationResolver {

	private final SqlScriptExecutorFactory sqlScriptExecutorFactory;

	private final ResourceProvider resourceProvider;

	private final SqlScriptFactory sqlScriptFactory;

	private final Configuration configuration;

	private final ParsingContext parsingContext;

	public SqlMigrationResolver(ResourceProvider resourceProvider, SqlScriptExecutorFactory sqlScriptExecutorFactory,
			SqlScriptFactory sqlScriptFactory, Configuration configuration, ParsingContext parsingContext) {
		this.sqlScriptExecutorFactory = sqlScriptExecutorFactory;
		this.resourceProvider = resourceProvider;
		this.sqlScriptFactory = sqlScriptFactory;
		this.configuration = configuration;
		this.parsingContext = parsingContext;
	}

	@Override
	public List<ResolvedMigration> resolveMigrations(Context context) {
		List<ResolvedMigration> migrations = new ArrayList<>();
		List<String> suffixes = configuration.getSqlMigrationSuffixes();
		addMigrations(migrations, configuration.getSqlMigrationPrefix(), suffixes, false);
		addMigrations(migrations, configuration.getRepeatableSqlMigrationPrefix(), suffixes, true);
		migrations.sort(new ResolvedMigrationComparator());
		return migrations;
	}

	private LoadableResource[] createPlaceholderReplacingLoadableResources(List<LoadableResource> loadableResources) {
		List<LoadableResource> list = new ArrayList<>();
		for (final LoadableResource loadableResource : loadableResources) {
			LoadableResource placeholderReplacingLoadableResource = new LoadableResource() {
				@Override
				public Reader read() {
					return PlaceholderReplacingReader.create(configuration, parsingContext, loadableResource.read());
				}

				@Override
				public String getAbsolutePath() {
					return loadableResource.getAbsolutePath();
				}

				@Override
				public String getAbsolutePathOnDisk() {
					return loadableResource.getAbsolutePathOnDisk();
				}

				@Override
				public String getFilename() {
					return loadableResource.getFilename();
				}

				@Override
				public String getRelativePath() {
					return loadableResource.getRelativePath();
				}
			};
			list.add(placeholderReplacingLoadableResource);
		}
		return list.toArray(new LoadableResource[0]);
	}

	private Integer getChecksumForLoadableResource(boolean repeatable, List<LoadableResource> loadableResources,
			ResourceName resourceName) {
		if (repeatable && configuration.isPlaceholderReplacement()) {
			parsingContext.updateFilenamePlaceholder(resourceName, configuration);
			return ChecksumCalculator.calculate(createPlaceholderReplacingLoadableResources(loadableResources));
		}
		return ChecksumCalculator.calculate(loadableResources.toArray(new LoadableResource[0]));
	}

	private @Nullable Integer getEquivalentChecksumForLoadableResource(boolean repeatable,
			List<LoadableResource> loadableResources) {
		if (repeatable) {
			return ChecksumCalculator.calculate(loadableResources.toArray(new LoadableResource[0]));
		}
		return null;
	}

	private void addMigrations(List<ResolvedMigration> migrations, String prefix, List<String> suffixes,
			boolean repeatable) {
		ResourceNameParser resourceNameParser = new ResourceNameParser(configuration);
		for (LoadableResource resource : resourceProvider.getResources(prefix, suffixes)) {
			String filename = resource.getFilename();
			ResourceName resourceName = resourceNameParser.parse(filename);
			if (!resourceName.isValid() || isSqlCallback(resourceName) || !prefix.equals(resourceName.getPrefix())) {
				continue;
			}
			SqlScript sqlScript = sqlScriptFactory.createSqlScript(resource, configuration.isMixed(), resourceProvider);
			List<LoadableResource> resources = new ArrayList<>();
			resources.add(resource);
			Integer checksum = getChecksumForLoadableResource(repeatable, resources, resourceName);
			Integer equivalentChecksum = getEquivalentChecksumForLoadableResource(repeatable, resources);
			migrations.add(new ResolvedMigrationImpl(resourceName.getVersion(), resourceName.getDescription(),
					resource.getRelativePath(), checksum, equivalentChecksum, MigrationType.SQL,
					resource.getAbsolutePathOnDisk(), new SqlMigrationExecutor(sqlScriptExecutorFactory, sqlScript)));
		}
	}

	/**
	 * Checks whether this filename is actually a sql-based callback instead of a regular
	 * migration.
	 * @param result The parsing result to check.
	 */
	protected static boolean isSqlCallback(ResourceName result) {
		return Event.fromId(result.getPrefix()) != null;
	}

}
