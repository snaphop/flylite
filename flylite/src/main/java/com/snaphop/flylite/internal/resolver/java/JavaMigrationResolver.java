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
package com.snaphop.flylite.internal.resolver.java;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.snaphop.flylite.api.ClassProvider;
import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.api.migration.JavaMigration;
import com.snaphop.flylite.api.resolver.MigrationResolver;
import com.snaphop.flylite.api.resolver.ResolvedMigration;
import com.snaphop.flylite.internal.resolver.ResolvedMigrationComparator;
import com.snaphop.flylite.internal.resolver.ResolvedMigrationImpl;
import com.snaphop.flylite.internal.util.ClassUtils;

/**
 * Resolves Java migrations either from configured instances or from scanned classes.
 */
public final class JavaMigrationResolver implements MigrationResolver {

	private final Collection<JavaMigration> javaMigrations;

	private final ClassProvider<JavaMigration> classProvider;

	private final Configuration configuration;

	private JavaMigrationResolver(Collection<JavaMigration> javaMigrations, ClassProvider<JavaMigration> classProvider,
			Configuration configuration) {
		this.javaMigrations = javaMigrations;
		this.classProvider = classProvider;
		this.configuration = configuration;
	}

	/**
	 * Resolves the configured java migration instances.
	 * @param configuration config.
	 * @return resolver.
	 */
	public static JavaMigrationResolver fixed(Configuration configuration) {
		return new JavaMigrationResolver(configuration.getJavaMigrations(), List::of, configuration);
	}

	/**
	 * Resolves java migrations found by scanning.
	 * @param classProvider scanned classes.
	 * @param configuration config.
	 * @return resolver.
	 */
	public static JavaMigrationResolver scanning(ClassProvider<JavaMigration> classProvider,
			Configuration configuration) {
		return new JavaMigrationResolver(List.of(), classProvider, configuration);
	}

	@Override
	public List<ResolvedMigration> resolveMigrations(Context context) {
		List<ResolvedMigration> migrations = new ArrayList<>();
		for (JavaMigration javaMigration : javaMigrations) {
			migrations.add(resolve(javaMigration));
		}
		for (Class<? extends JavaMigration> clazz : classProvider.getClasses()) {
			JavaMigration javaMigration = ClassUtils.instantiate(clazz.getName(), configuration.getClassLoader());
			migrations.add(resolve(javaMigration));
		}
		migrations.sort(new ResolvedMigrationComparator());
		return migrations;
	}

	private static ResolvedMigration resolve(JavaMigration javaMigration) {
		return new ResolvedMigrationImpl(javaMigration.getVersion(), javaMigration.getDescription(),
				javaMigration.getClass().getName(), javaMigration.getChecksum(), null, javaMigration.getType(),
				ClassUtils.getLocationOnDisk(javaMigration.getClass()), new JavaMigrationExecutor(javaMigration));
	}

}
