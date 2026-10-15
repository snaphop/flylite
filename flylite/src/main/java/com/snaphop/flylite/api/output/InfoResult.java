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

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The result of info.
 */
public final class InfoResult extends OperationResultBase {

	/**
	 * The current schema version.
	 */
	public @Nullable String schemaVersion;

	/**
	 * Comma separated configured schemas.
	 */
	public String schemaName;

	/**
	 * Info about every migration.
	 */
	public List<InfoOutput> migrations;

	/**
	 * Whether all schemas are empty.
	 */
	public boolean allSchemasEmpty;

	public InfoResult(String flyliteVersion, String database, @Nullable String schemaVersion, String schemaName,
			List<InfoOutput> migrations, boolean allSchemasEmpty) {
		super(flyliteVersion, database, "info");
		this.schemaVersion = schemaVersion;
		this.schemaName = schemaName;
		this.migrations = migrations;
		this.allSchemasEmpty = allSchemasEmpty;
	}

}
