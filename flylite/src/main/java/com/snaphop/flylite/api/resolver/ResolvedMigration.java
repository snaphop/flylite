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
package com.snaphop.flylite.api.resolver;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.api.MigrationType;
import com.snaphop.flylite.api.MigrationVersion;
import com.snaphop.flylite.api.executor.MigrationExecutor;

/**
 * Migration resolved through a MigrationResolver. Can be applied against a database.
 */
public interface ResolvedMigration {

	/**
	 * The version of the database after applying this migration.
	 * @return version or {@code null} for repeatable migrations.
	 */
	@Nullable MigrationVersion getVersion();

	/**
	 * Whether this is a versioned migration.
	 * @return true if versioned.
	 */
	default boolean isVersioned() {
		return getVersion() != null;
	}

	/**
	 * The description of the migration.
	 * @return description.
	 */
	String getDescription();

	/**
	 * The name of the script to execute for this migration, relative to its base
	 * (classpath/filesystem) location.
	 * @return script.
	 */
	String getScript();

	/**
	 * The checksum of the migration.
	 * @return checksum or {@code null} if no unique checksum is computable.
	 */
	@Nullable Integer getChecksum();

	/**
	 * The type of migration (SQL, JDBC, ...).
	 * @return type.
	 */
	MigrationType getType();

	/**
	 * The physical location of the migration on disk. Used for more precise error
	 * reporting in case of conflict.
	 * @return location or {@code null} if unknown.
	 */
	@Nullable String getPhysicalLocation();

	/**
	 * The executor to run this migration.
	 * @return executor.
	 */
	MigrationExecutor getExecutor();

	/**
	 * Whether the checksum of an applied migration matches this one.
	 * @param checksum checksum of the applied migration.
	 * @return true if it matches.
	 */
	boolean checksumMatches(@Nullable Integer checksum);

	/**
	 * Whether the checksum of an applied migration matches this one without being the
	 * same checksum.
	 * @param checksum checksum of the applied migration.
	 * @return true if it matches but is not identical.
	 */
	boolean checksumMatchesWithoutBeingIdentical(@Nullable Integer checksum);

	/**
	 * Whether this migration can be compared with the other one for duplicates.
	 * @param o other.
	 * @return true by default.
	 */
	default boolean canCompareWith(ResolvedMigration o) {
		return true;
	}

}
