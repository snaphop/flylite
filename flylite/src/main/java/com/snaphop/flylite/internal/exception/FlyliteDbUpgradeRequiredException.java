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
package com.snaphop.flylite.internal.exception;

import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.internal.database.DatabaseType;

/**
 * Thrown when an attempt was made to migrate an outdated database version not supported
 * by Flylite.
 */
public class FlyliteDbUpgradeRequiredException extends FlyliteException {

	public FlyliteDbUpgradeRequiredException(DatabaseType databaseType, String version, String minimumVersion) {
		super(databaseType.getName() + " upgrade required: " + databaseType.getName() + " " + version
				+ " is outdated and no longer supported by Flylite. Flylite currently supports "
				+ databaseType.getName() + " " + minimumVersion + " and newer.");
	}

}