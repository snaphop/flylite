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
package com.snaphop.flylite.api.pattern;

import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.MigrationState;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

public final class ValidatePattern {

	private final String migrationType;

	private final String migrationState;

	private static final List<String> validMigrationTypes = Arrays.asList("*", "repeatable", "versioned");

	private static final List<String> validMigrationStates = Arrays.asList("*",
			MigrationState.MISSING_SUCCESS.getPattern(), MigrationState.PENDING.getPattern(),
			MigrationState.IGNORED.getPattern(), MigrationState.FUTURE_SUCCESS.getPattern());

	private final String pattern;

	// String.split drops trailing empty strings, which is relied upon here
	@SuppressWarnings("StringSplitter")
	public static ValidatePattern fromPattern(String pattern) {
		String[] patternParts = pattern.split(":");
		if (patternParts.length != 2) {
			throw new FlyliteException("Invalid pattern \'" + pattern
					+ "\'. Pattern must be of the form <migration_type>:<migration_state> ");
		}
		String migrationType = patternParts[0].trim().toLowerCase(Locale.ROOT);
		String migrationState = patternParts[1].trim().toLowerCase(Locale.ROOT);
		if (!validMigrationTypes.contains(migrationType)) {
			throw new FlyliteException(
					"Invalid migration type \'" + patternParts[0] + "\'. Valid types are: " + validMigrationTypes);
		}
		if (!validMigrationStates.contains(migrationState)) {
			throw new FlyliteException(
					"Invalid migration state \'" + patternParts[1] + "\'. Valid states are: " + validMigrationStates);
		}
		return new ValidatePattern(migrationType, migrationState, pattern);
	}

	public boolean matchesMigration(boolean isVersioned, MigrationState state) {
		if (!state.getDisplayName().equalsIgnoreCase(migrationState) && !migrationState.equals("*")) {
			return false;
		}
		if (migrationType.equals("*")) {
			return true;
		}
		if (isVersioned && migrationType.equalsIgnoreCase("versioned")) {
			return true;
		}
		if (!isVersioned && migrationType.equalsIgnoreCase("repeatable")) {
			return true;
		}
		return false;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ValidatePattern that = (ValidatePattern) o;
		return Objects.equals(migrationType, that.migrationType) && Objects.equals(migrationState, that.migrationState);
	}

	@Override
	public int hashCode() {
		return Objects.hash(migrationType, migrationState);
	}

	@Override
	public String toString() {
		return pattern;
	}

	private ValidatePattern(final String migrationType, final String migrationState, final String pattern) {
		this.migrationType = migrationType;
		this.migrationState = migrationState;
		this.pattern = pattern;
	}

}
