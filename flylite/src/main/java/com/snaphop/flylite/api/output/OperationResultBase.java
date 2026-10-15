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

/**
 * Common fields of command results. The fields are public and mutable for compatibility
 * with Flyway's result objects.
 */
public abstract sealed class OperationResultBase implements OperationResult
		permits BaselineResult, CleanResult, InfoResult, MigrateResult, RepairResult, ValidateResult {

	/**
	 * Version of Flylite that ran the command.
	 */
	public String flyliteVersion;

	/**
	 * Database name.
	 */
	public String database;

	/**
	 * Warnings raised during the command.
	 */
	public List<String> warnings = new ArrayList<>();

	/**
	 * The command name.
	 */
	public String operation;

	OperationResultBase(String flyliteVersion, String database, String operation) {
		this.flyliteVersion = flyliteVersion;
		this.database = database;
		this.operation = operation;
	}

	/**
	 * Adds a warning.
	 * @param warning warning message.
	 */
	public void addWarning(String warning) {
		warnings.add(warning);
	}

}
