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
package com.snaphop.flylite.internal.schemahistory;

import java.util.Date;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.api.MigrationState;
import com.snaphop.flylite.api.MigrationType;
import com.snaphop.flylite.api.MigrationVersion;
import com.snaphop.flylite.api.resolver.ResolvedMigration;
import com.snaphop.flylite.internal.info.MigrationInfoContext;

/**
 * A migration applied to the database (maps to a row in the schema history table).
 */
public final class AppliedMigration implements Comparable<AppliedMigration> {

	private final int installedRank;

	private final @Nullable MigrationVersion version;

	private final String description;

	private final MigrationType type;

	private final String script;

	private final @Nullable Integer checksum;

	private final @Nullable Date installedOn;

	private final @Nullable String installedBy;

	private final int executionTime;

	private final boolean success;

	public AppliedMigration(int installedRank, @Nullable MigrationVersion version, String description,
			MigrationType type, String script, @Nullable Integer checksum, @Nullable Date installedOn,
			@Nullable String installedBy, int executionTime, boolean success) {
		this.installedRank = installedRank;
		this.version = version;
		this.description = description;
		this.type = type;
		this.script = script;
		this.checksum = checksum;
		this.installedOn = installedOn;
		this.installedBy = installedBy;
		this.executionTime = executionTime;
		this.success = success;
	}

	public int getInstalledRank() {
		return installedRank;
	}

	public @Nullable MigrationVersion getVersion() {
		return version;
	}

	public boolean isVersioned() {
		return version != null;
	}

	public String getDescription() {
		return description;
	}

	public MigrationType getType() {
		return type;
	}

	public String getScript() {
		return script;
	}

	public @Nullable Integer getChecksum() {
		return checksum;
	}

	public @Nullable Date getInstalledOn() {
		return installedOn;
	}

	public @Nullable String getInstalledBy() {
		return installedBy;
	}

	public int getExecutionTime() {
		return executionTime;
	}

	public boolean isSuccess() {
		return success;
	}

	public MigrationState getState(MigrationInfoContext context, boolean outOfOrder,
			@Nullable ResolvedMigration resolvedMigration) {
		if (MigrationType.DELETE == type) {
			return MigrationState.SUCCESS;
		}
		if (type.isBaseline()) {
			return MigrationState.BASELINE;
		}
		MigrationState missingState = getMissingState(context, resolvedMigration);
		if (missingState != null) {
			return missingState;
		}
		if (!success) {
			return MigrationState.FAILED;
		}
		if (MigrationType.SCHEMA == type) {
			return MigrationState.SUCCESS;
		}
		MigrationState repeatableState = getRepeatableState(context, resolvedMigration);
		if (repeatableState != null) {
			return repeatableState;
		}
		if (outOfOrder) {
			return MigrationState.OUT_OF_ORDER;
		}
		return MigrationState.SUCCESS;
	}

	private @Nullable MigrationState getRepeatableState(MigrationInfoContext context,
			@Nullable ResolvedMigration resolvedMigration) {
		if (version == null) {
			Integer latest = context.latestRepeatableRuns.get(description);
			if (latest != null && installedRank == latest) {
				if (resolvedMigration != null && resolvedMigration.checksumMatches(checksum)) {
					return MigrationState.SUCCESS;
				}
				return MigrationState.OUTDATED;
			}
			return MigrationState.SUPERSEDED;
		}
		return null;
	}

	private @Nullable MigrationState getMissingState(MigrationInfoContext context,
			@Nullable ResolvedMigration resolvedMigration) {
		if (resolvedMigration == null && isRepeatableLatest(context)) {
			if (MigrationType.SCHEMA == type) {
				return MigrationState.SUCCESS;
			}
			var v = version;
			if (v == null || v.compareTo(context.lastResolved) < 0) {
				return success ? MigrationState.MISSING_SUCCESS : MigrationState.MISSING_FAILED;
			}
			return success ? MigrationState.FUTURE_SUCCESS : MigrationState.FUTURE_FAILED;
		}
		return null;
	}

	private boolean isRepeatableLatest(MigrationInfoContext context) {
		if (version != null) {
			return true;
		}
		Integer latestRepeatableRank = context.latestRepeatableRuns.get(description);
		return latestRepeatableRank == null || installedRank == latestRepeatableRank;
	}

	@Override
	public int compareTo(AppliedMigration o) {
		return Integer.compare(installedRank, o.installedRank);
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof AppliedMigration that && executionTime == that.executionTime
				&& installedRank == that.installedRank && success == that.success
				&& Objects.equals(checksum, that.checksum) && description.equals(that.description)
				&& Objects.equals(installedBy, that.installedBy)
				&& Objects.equals(installedOn == null ? null : installedOn.toInstant(),
						that.installedOn == null ? null : that.installedOn.toInstant())
				&& script.equals(that.script) && type == that.type && Objects.equals(version, that.version);
	}

	@Override
	public int hashCode() {
		return Objects.hash(installedRank, version, description, type, script, checksum, installedOn, installedBy,
				executionTime, success);
	}

	@Override
	public String toString() {
		return "AppliedMigration[installedRank=" + installedRank + ", version=" + version + ", description="
				+ description + ", type=" + type + ", script=" + script + ", success=" + success + "]";
	}

}
