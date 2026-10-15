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
package com.snaphop.flylite.internal.resolver;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.api.ClassProvider;
import com.snaphop.flylite.api.ErrorCode;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.ResourceProvider;
import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.api.migration.JavaMigration;
import com.snaphop.flylite.api.resolver.MigrationResolver;
import com.snaphop.flylite.api.resolver.ResolvedMigration;
import com.snaphop.flylite.internal.parser.ParsingContext;
import com.snaphop.flylite.internal.resolver.java.JavaMigrationResolver;
import com.snaphop.flylite.internal.resolver.sql.SqlMigrationResolver;

import com.snaphop.flylite.internal.sqlscript.SqlScriptExecutorFactory;
import com.snaphop.flylite.internal.sqlscript.SqlScriptFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class CompositeMigrationResolver implements MigrationResolver {

	private final Collection<MigrationResolver> migrationResolvers = new ArrayList<>();

	private final ResourceProvider resourceProvider;

	private final SqlScriptFactory sqlScriptFactory;

	private final SqlScriptExecutorFactory sqlScriptExecutorFactory;

	private @Nullable List<ResolvedMigration> availableMigrations;

	public CompositeMigrationResolver(ResourceProvider resourceProvider, ClassProvider<JavaMigration> classProvider,
			Configuration configuration, SqlScriptExecutorFactory sqlScriptExecutorFactory,
			SqlScriptFactory sqlScriptFactory, ParsingContext parsingContext) {
		this.resourceProvider = resourceProvider;
		this.sqlScriptFactory = sqlScriptFactory;
		this.sqlScriptExecutorFactory = sqlScriptExecutorFactory;

		if (!configuration.isSkipDefaultResolvers()) {
			migrationResolvers.add(new SqlMigrationResolver(resourceProvider, sqlScriptExecutorFactory,
					sqlScriptFactory, configuration, parsingContext));
			migrationResolvers.add(JavaMigrationResolver.scanning(classProvider));

		}

		migrationResolvers.add(JavaMigrationResolver.fixed(configuration));
		migrationResolvers.addAll(configuration.getResolvers());
	}

	static void checkForIncompatibilities(List<ResolvedMigration> migrations) {
		ResolvedMigrationComparator resolvedMigrationComparator = new ResolvedMigrationComparator();

		// check for more than one migration with same version
		for (int i = 0; i < migrations.size() - 1; i++) {
			ResolvedMigration current = migrations.get(i);
			ResolvedMigration next = migrations.get(i + 1);

			if (current.canCompareWith(next) && next.canCompareWith(current)
					&& resolvedMigrationComparator.compare(current, next) == 0) {
				if (current.getVersion() != null) {
					throw new FlyliteException(
							String.format(
									"Found more than one migration with version %s\nOffenders:\n-> %s (%s)\n-> %s (%s)",
									current.getVersion(), current.getPhysicalLocation(), current.getType(),
									next.getPhysicalLocation(), next.getType()),
							ErrorCode.DUPLICATE_VERSIONED_MIGRATION);
				}
				throw new FlyliteException(String.format(
						"Found more than one repeatable migration with description %s\nOffenders:\n-> %s (%s)\n-> %s (%s)",
						current.getDescription(), current.getPhysicalLocation(), current.getType(),
						next.getPhysicalLocation(), next.getType()), ErrorCode.DUPLICATE_REPEATABLE_MIGRATION);
			}
		}
	}

	@Override
	public Collection<ResolvedMigration> resolveMigrations(Context context) {
		List<ResolvedMigration> migrations = availableMigrations;
		if (migrations == null) {
			migrations = doFindAvailableMigrations(context);
			availableMigrations = migrations;
		}
		return migrations;
	}

	public Collection<ResolvedMigration> resolveMigrations(Configuration configuration) {
		return resolveMigrations(
				new Context(configuration, resourceProvider, sqlScriptFactory, sqlScriptExecutorFactory));
	}

	private List<ResolvedMigration> doFindAvailableMigrations(Context context) throws FlyliteException {
		List<ResolvedMigration> migrations = new ArrayList<>(collectMigrations(migrationResolvers, context));
		migrations.sort(new ResolvedMigrationComparator());

		checkForIncompatibilities(migrations);

		return migrations;
	}

	Collection<ResolvedMigration> collectMigrations(Collection<MigrationResolver> migrationResolvers, Context context) {
		return migrationResolvers.stream()
			.flatMap(mr -> mr.resolveMigrations(context).stream())
			.collect(Collectors.toSet());
	}

}