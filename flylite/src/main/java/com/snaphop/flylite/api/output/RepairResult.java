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
 * The result of repair.
 */
public final class RepairResult extends OperationResultBase {

	/**
	 * Human readable actions taken.
	 */
	public List<String> repairActions = new ArrayList<>();

	/**
	 * Failed migrations removed from the schema history table.
	 */
	public List<RepairOutput> migrationsRemoved = new ArrayList<>();

	/**
	 * Missing migrations marked as deleted.
	 */
	public List<RepairOutput> migrationsDeleted = new ArrayList<>();

	/**
	 * Migrations whose checksum, description or type were realigned.
	 */
	public List<RepairOutput> migrationsAligned = new ArrayList<>();

	public RepairResult(String flyliteVersion, String database) {
		super(flyliteVersion, database, "repair");
	}

}
