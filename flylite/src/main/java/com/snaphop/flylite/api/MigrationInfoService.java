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
package com.snaphop.flylite.api;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.api.output.InfoResult;

/**
 * Info about all migrations.
 */
public interface MigrationInfoService {

	/**
	 * Retrieves the full set of infos about applied, current and future migrations.
	 * @return the full set of infos. An empty array if none.
	 */
	MigrationInfo[] all();

	/**
	 * Retrieves the information of the current applied migration, if any.
	 * @return the info or {@code null} if none.
	 */
	@Nullable MigrationInfo current();

	/**
	 * Retrieves the full set of infos about pending migrations, available locally, but
	 * not yet applied to the DB.
	 * @return the pending migrations. An empty array if none.
	 */
	MigrationInfo[] pending();

	/**
	 * Retrieves the full set of infos about the migrations applied to the DB.
	 * @return the applied migrations. An empty array if none.
	 */
	MigrationInfo[] applied();

	/**
	 * The info as a result object.
	 * @return info result.
	 */
	InfoResult getInfoResult();

}
