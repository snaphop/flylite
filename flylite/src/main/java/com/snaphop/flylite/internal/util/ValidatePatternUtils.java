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
package com.snaphop.flylite.internal.util;

import com.snaphop.flylite.api.MigrationState;
import com.snaphop.flylite.api.pattern.ValidatePattern;

import java.util.List;

public final class ValidatePatternUtils {

	private ValidatePatternUtils() {
	}

	public static boolean isPendingIgnored(List<ValidatePattern> ignorePatterns) {
		return ignorePatterns.stream().anyMatch(p -> p.equals(ValidatePattern.fromPattern("*:pending")))
				|| (ignorePatterns.stream().anyMatch(p -> p.matchesMigration(true, MigrationState.PENDING))
						&& ignorePatterns.stream().anyMatch(p -> p.matchesMigration(false, MigrationState.PENDING)));
	}

	public static boolean isIgnoredIgnored(List<ValidatePattern> ignorePatterns) {
		return ignorePatterns.stream().anyMatch(p -> p.equals(ValidatePattern.fromPattern("*:ignored")))
				|| (ignorePatterns.stream().anyMatch(p -> p.matchesMigration(true, MigrationState.IGNORED))
						&& ignorePatterns.stream().anyMatch(p -> p.matchesMigration(false, MigrationState.IGNORED)));
	}

	public static boolean isMissingIgnored(List<ValidatePattern> ignorePatterns) {
		return ignorePatterns.stream().anyMatch(p -> p.equals(ValidatePattern.fromPattern("*:missing")))
				|| (ignorePatterns.stream().anyMatch(p -> p.matchesMigration(true, MigrationState.MISSING_SUCCESS))
						&& ignorePatterns.stream()
							.anyMatch(p -> p.matchesMigration(false, MigrationState.MISSING_SUCCESS)));
	}

	public static boolean isFutureIgnored(List<ValidatePattern> ignorePatterns) {
		return ignorePatterns.stream().anyMatch(p -> p.equals(ValidatePattern.fromPattern("*:future")))
				|| (ignorePatterns.stream().anyMatch(p -> p.matchesMigration(true, MigrationState.FUTURE_SUCCESS))
						&& ignorePatterns.stream()
							.anyMatch(p -> p.matchesMigration(false, MigrationState.FUTURE_SUCCESS)));
	}

	public static List<ValidatePattern> getIgnoreAllPattern() {
		return List.of(ValidatePattern.fromPattern("*:*"));
	}

}