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

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.snaphop.flylite.api.MigrationInfo;
import com.snaphop.flylite.api.MigrationVersion;
import com.snaphop.flylite.internal.util.AsciiTable;
import com.snaphop.flylite.internal.util.DateUtils;

/**
 * Dumps migrations in an ascii-art table.
 */
public final class MigrationInfoDumper {

	private MigrationInfoDumper() {
	}

	/**
	 * Dumps the info about all migrations into an ascii table.
	 * @param migrationInfos the list of migrationInfos to dump.
	 * @return the ascii table, as one big multi-line string.
	 */
	public static String dumpToAsciiTable(MigrationInfo[] migrationInfos) {
		List<String> columns = List.of("Category", "Version", "Description", "Type", "Installed On", "State");
		List<List<@Nullable String>> rows = new ArrayList<>();
		for (MigrationInfo migrationInfo : migrationInfos) {
			rows.add(List.<@Nullable String>of(getCategory(migrationInfo), getVersionStr(migrationInfo),
					migrationInfo.getDescription(), migrationInfo.getType().name(),
					DateUtils.formatDateAsIsoString(migrationInfo.getInstalledOn()),
					migrationInfo.getState().getDisplayName()));
		}
		return new AsciiTable(columns, rows, true, "", "No migrations found").render();
	}

	static String getCategory(MigrationInfo migrationInfo) {
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

	private static String getVersionStr(MigrationInfo migrationInfo) {
		MigrationVersion version = migrationInfo.getVersion();
		return version == null ? "" : version.toString();
	}

}
