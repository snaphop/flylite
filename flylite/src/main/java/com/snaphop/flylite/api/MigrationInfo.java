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
package com.snaphop.flylite.api;

import java.util.Date;

import org.jspecify.annotations.Nullable;

/**
 * Info about a migration.
 */
public interface MigrationInfo extends Comparable<MigrationInfo> {

	/**
	 * The type of migration (BASELINE, SQL, JDBC, ...).
	 * @return type.
	 */
	MigrationType getType();

	/**
	 * The target version of this migration.
	 * @return the checksum or {@code null} if none.
	 */
	@Nullable Integer getChecksum();

	/**
	 * Whether this migration is versioned (not repeatable).
	 * @return true if versioned.
	 */
	default boolean isVersioned() {
		return getVersion() != null;
	}

	/**
	 * The schema version after the migration is complete.
	 * @return version or {@code null} for repeatable migrations.
	 */
	@Nullable MigrationVersion getVersion();

	/**
	 * The description of the migration.
	 * @return description.
	 */
	String getDescription();

	/**
	 * The name of the script to execute for this migration, relative to its location.
	 * @return script.
	 */
	String getScript();

	/**
	 * The state of the migration.
	 * @return state.
	 */
	MigrationState getState();

	/**
	 * When this migration was installed.
	 * @return timestamp or {@code null} if not applied.
	 */
	@Nullable Date getInstalledOn();

	/**
	 * The user that installed this migration.
	 * @return user or {@code null} if not applied.
	 */
	@Nullable String getInstalledBy();

	/**
	 * The rank of this installed migration.
	 * @return rank or {@code null} if not applied.
	 */
	@Nullable Integer getInstalledRank();

	/**
	 * The execution time (in millis) of this migration.
	 * @return execution time or {@code null} if not applied.
	 */
	@Nullable Integer getExecutionTime();

	/**
	 * The physical location of the migration on disk.
	 * @return location or {@code null} if not resolved.
	 */
	@Nullable String getPhysicalLocation();

	/**
	 * Compares versions with another migration.
	 * @param o other.
	 * @return comparison.
	 */
	int compareVersion(MigrationInfo o);

}
