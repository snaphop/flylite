/*
 * Ported from Flyway 4.2 (flyway-core/src/test/java/org/flywaydb/core/internal/dbsupport/sqlite/SQLiteMigrationMediumTest.java).
 * Copyright 2010-2017 Boxfuse GmbH. Modifications Copyright (C) Snaphop 2026.
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
package com.snaphop.flylite.upstream;

import org.junit.jupiter.api.Test;

import com.snaphop.flylite.Flylite;
import com.snaphop.flylite.test.Db;

/**
 * Flyway's SQLite tests. migrateMultipleSchemas, setCurrentSchema and schemaExists were
 * not supported by SQLite upstream either.
 */
class UpstreamSQLiteMigrationTest extends UpstreamMigrationTestCase {

	@Override
	Db db() {
		return Db.SQLITE;
	}

	@Test
	void trigger() {
		Flylite flyway = flyway().locations(MIGRATIONDIR + "/dbsupport/sqlite/sql/trigger").load();
		flyway.migrate();
		flyway.clean();
		flyway.migrate();
	}

	@Test
	void noDriverCrashIssue746() {
		Flylite flyway = Flylite.configure().dataSource("jdbc:sqlite::memory:", "sa", null).locations(BASEDIR).load();
		flyway.info();
		flyway.migrate();
	}

	@Test
	void cleanWithSystemTables() {
		// AUTOINCREMENT field causes sqlite_sequence table creation
		Flylite flyway = flyway().locations(MIGRATIONDIR + "/dbsupport/sqlite/sql/autoincrement").load();
		flyway.migrate();
		// crashed on "DROP sqlite_sequence": table sqlite_sequence may not be dropped
		flyway.clean();
	}

	@Test
	void foreignKey() {
		Flylite flyway = flyway().locations(MIGRATIONDIR + "/dbsupport/sqlite/sql/foreign_key")
			.jdbcProperties(java.util.Map.of("foreign_keys", "true"))
			.load();
		flyway.migrate();
		flyway.clean();
		flyway.migrate();
	}

}
