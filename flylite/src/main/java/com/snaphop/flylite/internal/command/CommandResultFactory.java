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
package com.snaphop.flylite.internal.command;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.api.ErrorDetails;
import com.snaphop.flylite.api.MigrationInfo;
import com.snaphop.flylite.api.MigrationVersion;
import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.api.output.BaselineResult;
import com.snaphop.flylite.api.output.CleanResult;
import com.snaphop.flylite.api.output.InfoOutput;
import com.snaphop.flylite.api.output.InfoResult;
import com.snaphop.flylite.api.output.MigrateOutput;
import com.snaphop.flylite.api.output.MigrateResult;
import com.snaphop.flylite.api.output.RepairOutput;
import com.snaphop.flylite.api.output.RepairResult;
import com.snaphop.flylite.api.output.ValidateOutput;
import com.snaphop.flylite.api.output.ValidateResult;
import com.snaphop.flylite.internal.database.base.Database;
import com.snaphop.flylite.internal.schemahistory.AppliedMigration;
import com.snaphop.flylite.internal.util.FlyliteVersion;

/**
 * Creates the result objects returned by commands.
 */
public final class CommandResultFactory {

	private CommandResultFactory() {
	}

	public static InfoResult createInfoResult(Configuration configuration, Database<?> database,
			MigrationInfo[] migrationInfos, @Nullable MigrationInfo current, boolean allSchemasEmpty) {
		List<InfoOutput> infoOutputs = new ArrayList<>();
		for (MigrationInfo migrationInfo : migrationInfos) {
			infoOutputs.add(createInfoOutput(migrationInfo));
		}
		MigrationVersion currentSchemaVersion = current == null ? null : current.getVersion();
		String schemaVersion = (currentSchemaVersion == null ? MigrationVersion.EMPTY : currentSchemaVersion)
			.getVersion();
		return new InfoResult(FlyliteVersion.getVersion(), getDatabaseName(database), schemaVersion,
				String.join(", ", configuration.getSchemas()), infoOutputs, allSchemasEmpty);
	}

	public static MigrateResult createMigrateResult(String databaseName, Configuration configuration) {
		return new MigrateResult(FlyliteVersion.getVersion(), databaseName,
				String.join(", ", configuration.getSchemas()));
	}

	public static CleanResult createCleanResult(String databaseName) {
		return new CleanResult(FlyliteVersion.getVersion(), databaseName);
	}

	public static BaselineResult createBaselineResult(String databaseName) {
		return new BaselineResult(FlyliteVersion.getVersion(), databaseName);
	}

	public static ValidateResult createValidateResult(String databaseName, @Nullable ErrorDetails validationError,
			int validationCount, @Nullable List<ValidateOutput> invalidMigrations, List<String> warnings) {
		boolean validationSuccessful = validationError == null;
		List<ValidateOutput> invalidMigrationsList = invalidMigrations == null ? new ArrayList<>() : invalidMigrations;
		return new ValidateResult(FlyliteVersion.getVersion(), databaseName, validationError, validationSuccessful,
				validationCount, invalidMigrationsList, warnings);
	}

	public static RepairResult createRepairResult(String databaseName) {
		return new RepairResult(FlyliteVersion.getVersion(), databaseName);
	}

	public static InfoOutput createInfoOutput(MigrationInfo migrationInfo) {
		MigrationVersion version = migrationInfo.getVersion();
		Date installedOn = migrationInfo.getInstalledOn();
		String installedBy = migrationInfo.getInstalledBy();
		Integer executionTime = migrationInfo.getExecutionTime();
		return new InfoOutput(getCategory(migrationInfo), version != null ? version.getVersion() : "",
				version != null ? version.getRawVersion() : "", migrationInfo.getDescription(),
				migrationInfo.getType().toString(), installedOn != null ? installedOn.toInstant().toString() : "",
				migrationInfo.getState().getDisplayName(), physicalLocation(migrationInfo),
				installedBy != null ? installedBy : "", executionTime != null ? executionTime : 0);
	}

	public static MigrateOutput createMigrateOutput(MigrationInfo migrationInfo, int executionTime) {
		return new MigrateOutput(getCategory(migrationInfo), versionString(migrationInfo.getVersion()),
				migrationInfo.getDescription(), migrationInfo.getType().toString(), physicalLocation(migrationInfo),
				executionTime);
	}

	public static ValidateOutput createValidateOutput(MigrationInfo migrationInfo, ErrorDetails validateError) {
		return new ValidateOutput(versionString(migrationInfo.getVersion()), migrationInfo.getDescription(),
				physicalLocation(migrationInfo), validateError);
	}

	public static RepairOutput createRepairOutput(MigrationInfo migrationInfo) {
		return new RepairOutput(versionString(migrationInfo.getVersion()), migrationInfo.getDescription(),
				physicalLocation(migrationInfo));
	}

	public static RepairOutput createRepairOutput(AppliedMigration am) {
		return new RepairOutput(versionString(am.getVersion()), am.getDescription(), "");
	}

	private static String versionString(@Nullable MigrationVersion version) {
		return version != null ? version.getVersion() : "";
	}

	private static String physicalLocation(MigrationInfo migrationInfo) {
		String location = migrationInfo.getPhysicalLocation();
		return location != null ? location : "";
	}

	private static String getDatabaseName(Database<?> database) {
		try {
			return database.getCatalog();
		}
		catch (Exception e) {
			return "";
		}
	}

	private static String getCategory(MigrationInfo migrationInfo) {
		if (migrationInfo.getType().isSynthetic()) {
			return "";
		}
		if (migrationInfo.getVersion() == null) {
			return "Repeatable";
		}
		if (migrationInfo.getType().isBaseline()) {
			return "Baseline";
		}
		return "Versioned";
	}

}
