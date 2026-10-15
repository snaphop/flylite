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
package com.snaphop.flylite.api.output;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The result of migrate.
 */
public sealed class MigrateResult extends OperationResultBase permits MigrateErrorResult {

	/**
	 * Schema version before migrating or {@code null} if the schema was empty.
	 */
	public @Nullable String initialSchemaVersion;

	/**
	 * Schema version after migrating or {@code null} if nothing was migrated.
	 */
	public @Nullable String targetSchemaVersion;

	/**
	 * Comma separated configured schemas.
	 */
	public String schemaName;

	/**
	 * Migrations executed.
	 */
	public List<MigrateOutput> migrations;

	/**
	 * Number of migrations executed.
	 */
	public int migrationsExecuted;

	/**
	 * Whether migrate succeeded.
	 */
	public boolean success;

	public MigrateResult(String flyliteVersion, String database, String schemaName) {
		super(flyliteVersion, database, "migrate");
		this.schemaName = schemaName;
		this.migrations = new ArrayList<>();
		this.success = true;
	}

	MigrateResult(MigrateResult migrateResult) {
		super(migrateResult.flyliteVersion, migrateResult.database, migrateResult.operation);
		this.schemaName = migrateResult.schemaName;
		this.migrations = migrateResult.migrations;
		this.success = migrateResult.success;
		this.migrationsExecuted = migrateResult.migrationsExecuted;
		this.initialSchemaVersion = migrateResult.initialSchemaVersion;
		this.targetSchemaVersion = migrateResult.targetSchemaVersion;
		this.warnings = migrateResult.warnings;
	}

}
