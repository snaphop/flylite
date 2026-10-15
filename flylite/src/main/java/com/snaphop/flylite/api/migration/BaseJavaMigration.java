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
package com.snaphop.flylite.api.migration;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.MigrationVersion;
import com.snaphop.flylite.internal.resolver.MigrationInfoHelper;

/**
 * This is the recommended class to extend for implementing Java-based Migrations.
 * <p>
 * Subclasses should follow the default naming convention of having a class name with the
 * following structure:
 * <ul>
 * <li><strong>Versioned Migrations:</strong> V2__Add_new_table</li>
 * <li><strong>Repeatable Migrations:</strong> R__Add_new_table</li>
 * </ul>
 * <p>
 * The class name consists of the following parts:
 * <ul>
 * <li><strong>Prefix:</strong> V for versioned migrations, R for repeatable
 * migrations</li>
 * <li><strong>Version:</strong> Underscores (automatically replaced by dots at runtime)
 * separate as many parts as you like (Not for repeatable migrations)</li>
 * <li><strong>Separator:</strong> __ (two underscores)</li>
 * <li><strong>Description:</strong> Underscores (automatically replaced by spaces at
 * runtime) separate the words</li>
 * </ul>
 * <p>
 * If you need more control over the class name, implement {@link JavaMigration} directly.
 */
public abstract class BaseJavaMigration implements JavaMigration {

	private final @Nullable MigrationVersion version;

	private final String description;

	/**
	 * Creates a new instance of a Java-based migration following the default naming
	 * convention.
	 * @throws FlyliteException if the class name does not follow the convention.
	 */
	protected BaseJavaMigration() {
		String shortName = getClass().getSimpleName();
		boolean repeatable = shortName.startsWith("R");
		if (!shortName.startsWith("V") && !repeatable) {
			throw new FlyliteException("Invalid Java-based migration class name: " + getClass().getName()
					+ " => ensure it starts with V or R"
					+ " or implement com.snaphop.flylite.api.migration.JavaMigration directly for non-default naming");
		}
		var info = MigrationInfoHelper.extractVersionAndDescription(shortName, shortName.substring(0, 1), "__",
				new String[] { "" }, repeatable);
		this.version = info.getLeft();
		this.description = info.getRight();
	}

	@Override
	public @Nullable MigrationVersion getVersion() {
		return version;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public @Nullable Integer getChecksum() {
		return null;
	}

	@Override
	public boolean canExecuteInTransaction() {
		return true;
	}

}
