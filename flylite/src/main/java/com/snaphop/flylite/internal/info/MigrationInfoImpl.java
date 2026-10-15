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

import java.util.Date;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.api.ErrorCode;
import com.snaphop.flylite.api.ErrorDetails;
import com.snaphop.flylite.api.MigrationInfo;
import com.snaphop.flylite.api.MigrationState;
import com.snaphop.flylite.api.MigrationType;
import com.snaphop.flylite.api.MigrationVersion;
import com.snaphop.flylite.api.resolver.ResolvedMigration;
import com.snaphop.flylite.internal.resolver.ResolvedMigrationImpl;
import com.snaphop.flylite.internal.schemahistory.AppliedMigration;
import com.snaphop.flylite.internal.schemahistory.SchemaHistory;
import com.snaphop.flylite.internal.util.AbbreviationUtils;

/**
 * Info about a migration. Either the resolved or the applied migration (or both) is
 * present.
 */
public final class MigrationInfoImpl implements MigrationInfo {

	private final @Nullable ResolvedMigration resolvedMigration;

	private final @Nullable AppliedMigration appliedMigration;

	private final MigrationInfoContext context;

	private final boolean outOfOrder;

	private final boolean deleted;

	private final boolean shouldNotExecuteMigration;

	MigrationInfoImpl(@Nullable ResolvedMigration resolvedMigration, @Nullable AppliedMigration appliedMigration,
			MigrationInfoContext context, boolean outOfOrder, boolean deleted) {
		if (resolvedMigration == null && appliedMigration == null) {
			throw new IllegalArgumentException("resolved or applied migration required");
		}
		this.resolvedMigration = resolvedMigration;
		this.appliedMigration = appliedMigration;
		this.context = context;
		this.outOfOrder = outOfOrder;
		this.deleted = deleted;
		this.shouldNotExecuteMigration = resolvedMigration != null && !resolvedMigration.getExecutor().shouldExecute();
	}

	/**
	 * The resolved migration to aggregate the info from.
	 * @return resolved migration or {@code null} if not resolved locally.
	 */
	public @Nullable ResolvedMigration getResolvedMigration() {
		return resolvedMigration;
	}

	/**
	 * The applied migration to aggregate the info from.
	 * @return applied migration or {@code null} if not applied.
	 */
	public @Nullable AppliedMigration getAppliedMigration() {
		return appliedMigration;
	}

	private ResolvedMigration resolved() {
		return Objects.requireNonNull(resolvedMigration);
	}

	@Override
	public MigrationType getType() {
		var am = appliedMigration;
		return am != null ? am.getType() : resolved().getType();
	}

	@Override
	public @Nullable Integer getChecksum() {
		var am = appliedMigration;
		return am != null ? am.getChecksum() : resolved().getChecksum();
	}

	@Override
	public boolean isVersioned() {
		var am = appliedMigration;
		return am != null ? am.isVersioned() : resolved().isVersioned();
	}

	@Override
	public @Nullable MigrationVersion getVersion() {
		var am = appliedMigration;
		return am != null ? am.getVersion() : resolved().getVersion();
	}

	@Override
	public String getDescription() {
		var am = appliedMigration;
		return am != null ? am.getDescription() : resolved().getDescription();
	}

	@Override
	public String getScript() {
		var am = appliedMigration;
		return am != null ? am.getScript() : resolved().getScript();
	}

	@Override
	public MigrationState getState() {
		if (deleted) {
			return MigrationState.DELETED;
		}
		var am = appliedMigration;
		if (am == null) {
			if (shouldNotExecuteMigration) {
				return MigrationState.IGNORED;
			}
			return resolved().getState(context);
		}
		return am.getState(context, outOfOrder, resolvedMigration);
	}

	@Override
	public @Nullable Date getInstalledOn() {
		var am = appliedMigration;
		return am != null ? am.getInstalledOn() : null;
	}

	@Override
	public @Nullable String getInstalledBy() {
		var am = appliedMigration;
		return am != null ? am.getInstalledBy() : null;
	}

	@Override
	public @Nullable Integer getInstalledRank() {
		var am = appliedMigration;
		return am != null ? am.getInstalledRank() : null;
	}

	@Override
	public @Nullable Integer getExecutionTime() {
		var am = appliedMigration;
		return am != null ? am.getExecutionTime() : null;
	}

	@Override
	public String getPhysicalLocation() {
		var rm = resolvedMigration;
		if (rm != null) {
			String location = rm.getPhysicalLocation();
			return location == null ? "" : location;
		}
		return "";
	}

	/**
	 * Validates this migration.
	 * @return the error code with the relevant validation message, or {@code null} if
	 * everything is fine.
	 */
	public @Nullable ErrorDetails validate() {
		MigrationState state = getState();
		var rm = resolvedMigration;
		var am = appliedMigration;
		var version = getVersion();

		if (MigrationState.ABOVE_TARGET.equals(state)) {
			return null;
		}

		if (MigrationState.DELETED.equals(state)) {
			return null;
		}

		if (context.ignorePatterns.stream().anyMatch(p -> p.matchesMigration(version != null, state))) {
			return null;
		}

		if (state.isFailed() && (!context.isFutureIgnored() || MigrationState.FUTURE_FAILED != state)) {
			if (version == null) {
				String errorMessage = "Detected failed repeatable migration: " + getDescription()
						+ ".\nPlease remove any half-completed changes then run repair to fix the schema history.";
				return new ErrorDetails(ErrorCode.FAILED_REPEATABLE_MIGRATION, errorMessage);
			}
			String errorMessage = "Detected failed migration to version " + version + " (" + getDescription() + ")"
					+ ".\nPlease remove any half-completed changes then run repair to fix the schema history.";
			return new ErrorDetails(ErrorCode.FAILED_VERSIONED_MIGRATION, errorMessage);
		}

		if (rm == null && am != null && !am.getType().isSynthetic() && (MigrationState.SUPERSEDED != state)
				&& (!context.isMissingIgnored()
						|| (MigrationState.MISSING_SUCCESS != state && MigrationState.MISSING_FAILED != state))
				&& (!context.isFutureIgnored()
						|| (MigrationState.FUTURE_SUCCESS != state && MigrationState.FUTURE_FAILED != state))) {
			if (am.getVersion() != null) {
				String errorMessage = "Detected applied migration not resolved locally: " + version
						+ ".\nIf you removed this migration intentionally, run repair to mark the migration as deleted.";
				return new ErrorDetails(ErrorCode.APPLIED_VERSIONED_MIGRATION_NOT_RESOLVED, errorMessage);
			}
			String errorMessage = "Detected applied migration not resolved locally: " + getDescription()
					+ ".\nIf you removed this migration intentionally, run repair to mark the migration as deleted.";
			return new ErrorDetails(ErrorCode.APPLIED_REPEATABLE_MIGRATION_NOT_RESOLVED, errorMessage);
		}

		if (rm != null && !context.isIgnoredIgnored() && MigrationState.IGNORED == state
				&& !rm.getType().isBaseline()) {
			if (shouldNotExecuteMigration) {
				return null;
			}
			if (version != null) {
				String errorMessage = "Detected resolved migration not applied to database: " + version
						+ ".\nTo ignore this migration, set ignoreMigrationPatterns='*:ignored'. To allow executing this migration, set outOfOrder=true.";
				return new ErrorDetails(ErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED, errorMessage);
			}
			String errorMessage = "Detected resolved repeatable migration not applied to database: " + getDescription()
					+ ".\nTo ignore this migration, set ignoreMigrationPatterns='*:ignored'.";
			return new ErrorDetails(ErrorCode.RESOLVED_REPEATABLE_MIGRATION_NOT_APPLIED, errorMessage);
		}

		if (!context.isPendingIgnored() && MigrationState.PENDING == state) {
			if (version != null) {
				String errorMessage = "Detected resolved migration not applied to database: " + version
						+ ".\nTo fix this error, either run migrate, or set ignoreMigrationPatterns='*:pending'.";
				return new ErrorDetails(ErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED, errorMessage);
			}
			String errorMessage = "Detected resolved repeatable migration not applied to database: " + getDescription()
					+ ".\nTo fix this error, either run migrate, or set ignoreMigrationPatterns='*:pending'.";
			return new ErrorDetails(ErrorCode.RESOLVED_REPEATABLE_MIGRATION_NOT_APPLIED, errorMessage);
		}

		if (!context.isPendingIgnored() && MigrationState.OUTDATED == state) {
			String errorMessage = "Detected outdated resolved repeatable migration that should be re-applied to database: "
					+ getDescription() + ".\nRun migrate to execute this migration.";
			return new ErrorDetails(ErrorCode.OUTDATED_REPEATABLE_MIGRATION, errorMessage);
		}

		if (rm != null && am != null && getType() != MigrationType.DELETE) {
			var appliedVersion = am.getVersion();
			String migrationIdentifier = appliedVersion == null ? am.getScript() : "version " + appliedVersion;
			var appliedBaseline = context.appliedBaseline;
			if (version == null || appliedBaseline == null || version.compareTo(appliedBaseline) > 0) {
				if (rm.getType() != am.getType()) {
					String mismatchMessage = createMismatchMessage("type", migrationIdentifier, am.getType(),
							rm.getType());
					return new ErrorDetails(ErrorCode.TYPE_MISMATCH, mismatchMessage);
				}
				if (rm.getVersion() != null || (context.isPendingIgnored() && MigrationState.OUTDATED != state
						&& MigrationState.SUPERSEDED != state)) {
					if (!rm.checksumMatches(am.getChecksum())) {
						String mismatchMessage = createMismatchMessage("checksum", migrationIdentifier,
								am.getChecksum(), rm.getChecksum());
						return new ErrorDetails(ErrorCode.CHECKSUM_MISMATCH, mismatchMessage);
					}
				}
				if (descriptionMismatch(rm, am)) {
					String mismatchMessage = createMismatchMessage("description", migrationIdentifier,
							am.getDescription(), rm.getDescription());
					return new ErrorDetails(ErrorCode.DESCRIPTION_MISMATCH, mismatchMessage);
				}
			}
		}

		// Perform additional validation for pending migrations. This is not performed for
		// previously applied migrations as it is assumed that if the checksum is
		// unchanged,
		// previous positive validation results still hold true.
		// #2392: Migrations above target are also ignored as the user explicitly asked
		// for
		// them to not be taken into account.
		if (!context.isPendingIgnored() && MigrationState.PENDING == state
				&& rm instanceof ResolvedMigrationImpl resolvedImpl) {
			resolvedImpl.validate();
		}

		return null;
	}

	private static boolean descriptionMismatch(ResolvedMigration resolvedMigration, AppliedMigration appliedMigration) {
		// For some databases, we can't put an empty description into the history table
		if (SchemaHistory.NO_DESCRIPTION_MARKER.equals(appliedMigration.getDescription())) {
			return !"".equals(resolvedMigration.getDescription());
		}
		return (!AbbreviationUtils.abbreviateDescription(resolvedMigration.getDescription())
			.equals(appliedMigration.getDescription()));
	}

	private static String createMismatchMessage(String mismatch, String migrationIdentifier, @Nullable Object applied,
			@Nullable Object resolved) {
		return String.format(
				"Migration " + mismatch + " mismatch for migration %s\n" + "-> Applied to database : %s\n"
						+ "-> Resolved locally    : %s\n"
						+ "Either revert the changes to the migration, or run repair to update the schema history.",
				migrationIdentifier, applied, resolved);
	}

	public boolean canExecuteInTransaction() {
		var rm = resolvedMigration;
		return rm != null && rm.getExecutor().canExecuteInTransaction();
	}

	@Override
	public int compareTo(MigrationInfo o) {
		Integer rank = getInstalledRank();
		Integer otherRank = o.getInstalledRank();
		if (rank != null && otherRank != null) {
			return rank.compareTo(otherRank);
		}

		MigrationState state = getState();
		MigrationState oState = o.getState();

		// Below baseline migrations come before applied ones
		if ((state == MigrationState.BELOW_BASELINE || state == MigrationState.BASELINE_IGNORED)
				&& oState.isApplied()) {
			return -1;
		}

		if (state.isApplied()
				&& (oState == MigrationState.BELOW_BASELINE || oState == MigrationState.BASELINE_IGNORED)) {
			return 1;
		}

		// Sort installed before pending
		if (rank != null) {
			return -1;
		}
		if (otherRank != null) {
			return 1;
		}

		return compareVersion(o);
	}

	@Override
	public int compareVersion(MigrationInfo o) {
		var version = getVersion();
		var otherVersion = o.getVersion();
		if (version != null && otherVersion != null) {
			return version.compareTo(otherVersion);
		}

		// One versioned and one repeatable migration: versioned migration goes before
		// repeatable
		if (version != null) {
			return -1;
		}
		if (otherVersion != null) {
			return 1;
		}

		// Two repeatable migrations: sort by description
		return getDescription().compareTo(o.getDescription());
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof MigrationInfoImpl that && context == that.context
				&& Objects.equals(appliedMigration, that.appliedMigration)
				&& Objects.equals(resolvedMigration, that.resolvedMigration);
	}

	@Override
	public int hashCode() {
		return Objects.hash(resolvedMigration, appliedMigration);
	}

}
