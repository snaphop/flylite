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
package com.snaphop.flylite.internal.resolver;

import com.snaphop.flylite.api.resolver.ResolvedMigration;

import java.util.Comparator;

public class ResolvedMigrationComparator implements Comparator<ResolvedMigration> {

	@Override
	public int compare(ResolvedMigration o1, ResolvedMigration o2) {
		var v1 = o1.getVersion();
		var v2 = o2.getVersion();
		if (v1 != null && v2 != null) {
			return v1.compareTo(v2);
		}
		if (v1 != null) {
			return -1;
		}
		if (v2 != null) {
			return 1;
		}
		return o1.getDescription().compareTo(o2.getDescription());
	}

}