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
package com.snaphop.flylite.internal.info;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.api.MigrationVersion;
import com.snaphop.flylite.api.pattern.ValidatePattern;
import com.snaphop.flylite.internal.util.ValidatePatternUtils;

/**
 * Mutable state shared while computing migration infos.
 */
public final class MigrationInfoContext {

	public boolean outOfOrder;

	public List<ValidatePattern> ignorePatterns = List.of();

	public @Nullable MigrationVersion target;

	public @Nullable MigrationVersion schema;

	public @Nullable MigrationVersion pendingBaseline;

	public @Nullable MigrationVersion appliedBaseline;

	public MigrationVersion lastResolved = MigrationVersion.EMPTY;

	public MigrationVersion lastApplied = MigrationVersion.EMPTY;

	public Map<String, Integer> latestRepeatableRuns = new HashMap<>();

	public boolean isPendingIgnored() {
		return ValidatePatternUtils.isPendingIgnored(ignorePatterns);
	}

	public boolean isIgnoredIgnored() {
		return ValidatePatternUtils.isIgnoredIgnored(ignorePatterns);
	}

	public boolean isMissingIgnored() {
		return ValidatePatternUtils.isMissingIgnored(ignorePatterns);
	}

	public boolean isFutureIgnored() {
		return ValidatePatternUtils.isFutureIgnored(ignorePatterns);
	}

}
