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

/**
 * The type of a migration. The names are stored in the {@code type} column of the schema
 * history table and are compatible with Flyway.
 */
public enum MigrationType {

	/**
	 * Schema creation migration.
	 */
	SCHEMA(true, false),
	/**
	 * Baseline migration.
	 */
	BASELINE(true, true),
	/**
	 * Deleted migration.
	 */
	DELETE(true, false),
	/**
	 * SQL migrations.
	 */
	SQL(false, false),
	/**
	 * JDBC Java-based migrations.
	 */
	JDBC(false, false),
	/**
	 * Migrations using custom MigrationResolvers.
	 */
	CUSTOM(false, false);

	private final boolean synthetic;

	private final boolean baseline;

	MigrationType(boolean synthetic, boolean baseline) {
		this.synthetic = synthetic;
		this.baseline = baseline;
	}

	/**
	 * Parses the type stored in the schema history table.
	 * @param migrationType type name.
	 * @return type.
	 * @throws FlyliteException if the type is not supported.
	 */
	public static MigrationType fromString(String migrationType) {
		// Convert legacy types to maintain compatibility
		if ("SPRING_JDBC".equals(migrationType)) {
			return JDBC;
		}
		try {
			return valueOf(migrationType);
		}
		catch (IllegalArgumentException e) {
			throw new FlyliteException("Unsupported migration type in schema history table: " + migrationType, e);
		}
	}

	/**
	 * Whether this is a synthetic migration type, which is only ever present in the
	 * schema history table, but never discovered by migration resolvers.
	 * @return true if synthetic.
	 */
	public boolean isSynthetic() {
		return synthetic;
	}

	/**
	 * Whether this is a baseline type.
	 * @return true if baseline.
	 */
	public boolean isBaseline() {
		return baseline;
	}

}
