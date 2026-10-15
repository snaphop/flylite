/*
 * Ported from Flyway 4.2 (flyway-core/src/test/java/org/flywaydb/core/migration/MigrationTestCase.java).
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snaphop.flylite.Flylite;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.MigrationInfo;
import com.snaphop.flylite.api.MigrationState;
import com.snaphop.flylite.api.MigrationType;
import com.snaphop.flylite.api.MigrationVersion;
import com.snaphop.flylite.api.configuration.FluentConfiguration;
import com.snaphop.flylite.test.Db;
import com.snaphop.flylite.test.Jdbc;

/**
 * Flyway's database independent migration tests. Every test gets a fresh database instead
 * of cleaning a shared one. Tests for removed features (Flyway 3 metadata table upgrade,
 * cleanOnValidationError, ignoreFailedFutureMigration) are not ported.
 */
abstract class UpstreamMigrationTestCase {

	static final String MIGRATIONDIR = "upstream/migration";

	static final String BASEDIR = MIGRATIONDIR + "/sql";

	@TempDir
	Path dir;

	Db.TestDatabase database;

	Jdbc jdbc;

	abstract Db db();

	@BeforeEach
	void setUp() {
		database = db().create(dir);
		jdbc = database.jdbc();
	}

	FluentConfiguration flyway() {
		return database.configure().cleanDisabled(false);
	}

	static MigrationVersion version(Flylite flylite) {
		MigrationInfo current = flylite.info().current();
		assertNotNull(current);
		return Objects.requireNonNull(current.getVersion());
	}

	@Test
	void migrate() {
		Flylite flyway = flyway().locations(BASEDIR).load();
		flyway.migrate();
		assertEquals("2.0", version(flyway).toString());
		assertEquals(0, flyway.migrate().migrationsExecuted);
		assertEquals(4, flyway.info().applied().length);
		assertEquals(2, jdbc.count("select count(*) from all_misters"));
		flyway.validate();
	}

	/**
	 * Upstream checked that the checksum Flyway 4 computed for this script is still
	 * computed the same way. Flylite must not change it either.
	 */
	@Test
	void checksumCompatibleWithFlyway4() {
		Flylite flyway = flyway().locations(BASEDIR).load();
		flyway.migrate();
		assertEquals(454910647, flyway.info().applied()[0].getChecksum());
	}

	@Test
	void target() {
		var config = flyway().locations(BASEDIR);
		Flylite flyway = config.target(MigrationVersion.fromVersion("1.2")).load();
		flyway.migrate();
		assertEquals("1.2", version(flyway).toString());
		assertEquals("Populate table", flyway.info().current().getDescription());

		flyway = config.target(MigrationVersion.fromVersion("1.0")).load();
		flyway.migrate();
		assertEquals("1.2", version(flyway).toString());

		flyway = config.target(MigrationVersion.LATEST).load();
		flyway.migrate();
		assertEquals("2.0", version(flyway).toString());
	}

	@Test
	void customTableName() {
		flyway().locations(BASEDIR).table("my_custom_table").load().migrate();
		assertEquals(4, jdbc.count("select count(*) from my_custom_table"));
	}

	@Test
	void validateFails() {
		var config = flyway().locations(BASEDIR).sqlMigrationSuffixes("First.sql");
		Flylite flyway = config.load();
		flyway.migrate();
		assertEquals("1", version(flyway).toString());
		assertThrows(FlyliteException.class,
				() -> config.ignoreMigrationPatterns(new String[0])
					.sqlMigrationPrefix("CheckValidate")
					.load()
					.validate());
	}

	@Test
	void validateMoreAppliedThanAvailable() {
		flyway().locations(BASEDIR).load().migrate();
		// the V1__First.sql of the validate location is different
		assertThrows(FlyliteException.class, () -> flyway().locations(MIGRATIONDIR + "/validate").load().validate());
	}

	@Test
	void failedMigration() {
		String tableName = "before_the_error";
		Flylite flyway = flyway().locations(MIGRATIONDIR + "/failed")
			.placeholders(Map.of("tableName", tableName))
			.load();
		var e = assertThrows(FlyliteException.class, flyway::migrate);
		assertTrue(e.getMessage().contains("Line       : 21"), e.getMessage());
		assertTrue(e.getMessage().contains("Statement  : THIS IS NOT VALID SQL"), e.getMessage());
		// both databases support DDL transactions
		assertFalse(jdbc.tableExists(tableName));
		assertNull(flyway.info().current());
	}

	@Test
	void futureFailedMigration() {
		var e = assertThrows(FlyliteException.class,
				() -> flyway().validateOnMigrate(false).locations(MIGRATIONDIR + "/future_failed").load().migrate());
		assertTrue(e.getMessage().contains("THIS IS NOT VALID SQL"), e.getMessage());
		flyway().validateOnMigrate(false).locations(BASEDIR).load().migrate();
	}

	@Test
	void group() {
		Flylite flyway = flyway().locations(MIGRATIONDIR + "/future_failed").group(true).load();
		assertEquals(4, flyway.info().all().length);
		assertThrows(FlyliteException.class, flyway::migrate);
		assertNull(flyway.info().current());
		assertEquals(4, flyway.info().pending().length);
	}

	@Test
	void repair() {
		Flylite flyway = flyway().locations(MIGRATIONDIR + "/future_failed").load();
		assertEquals(4, flyway.info().all().length);
		assertThrows(FlyliteException.class, flyway::migrate);
		assertEquals("2.0", version(flyway).toString());
		assertEquals(MigrationState.SUCCESS, flyway.info().current().getState());
		flyway.repair();
		assertEquals("2.0", version(flyway).toString());
		assertEquals(MigrationState.SUCCESS, flyway.info().current().getState());
	}

	@Test
	void repairChecksum() {
		Integer commentChecksum = flyway().locations(MIGRATIONDIR + "/comment").load().info().pending()[0]
			.getChecksum();
		Integer quoteChecksum = flyway().locations(MIGRATIONDIR + "/quote").load().info().pending()[0].getChecksum();
		assertNotEquals(commentChecksum, quoteChecksum);

		Flylite flyway = flyway().locations(MIGRATIONDIR + "/quote").load();
		flyway.migrate();
		assertEquals(quoteChecksum, flyway.info().applied()[0].getChecksum());

		flyway = flyway().locations(MIGRATIONDIR + "/comment").load();
		flyway.repair();
		assertEquals(commentChecksum, flyway.info().applied()[0].getChecksum());
	}

	@Test
	void quote() {
		flyway().locations(MIGRATIONDIR + "/quote").load().migrate();
		assertEquals(0, jdbc.count("SELECT COUNT(name) FROM \"table\""));
	}

	@Test
	void quotesAroundTableName() {
		Flylite flyway = flyway().locations(MIGRATIONDIR + "/quote").load();
		flyway.migrate();
		// Clean script must also be able to properly deal with these reserved keywords in
		// table names.
		flyway.clean();
	}

	@Test
	void checkValidationWithInitRow() {
		var config = flyway().locations(BASEDIR);
		Flylite flyway = config.target(MigrationVersion.fromVersion("1.1")).load();
		flyway.migrate();
		assertEquals("1.1", version(flyway).toString());

		jdbc.execute("DROP TABLE flyway_schema_history");
		config.baselineVersion("1.1").baselineDescription("initial version 1.1").load().baseline();

		flyway = config.target(MigrationVersion.LATEST).load();
		flyway.migrate();
		assertEquals("2.0", version(flyway).toString());
		flyway.validate();
	}

	@Test
	void isSchemaEmpty() {
		Flylite flyway = flyway().locations(BASEDIR).load();
		assertTrue(flyway.info().getInfoResult().allSchemasEmpty);
		flyway.migrate();
		assertFalse(flyway.info().getInfoResult().allSchemasEmpty);
		flyway.clean();
		assertTrue(flyway.info().getInfoResult().allSchemasEmpty);
	}

	@Test
	void nonEmptySchema() {
		createTestTable();
		assertThrows(FlyliteException.class, () -> flyway().locations(BASEDIR).load().migrate());
	}

	@Test
	void nonEmptySchemaWithInit() {
		createTestTable();
		var config = flyway().locations(BASEDIR).baselineVersion("0");
		config.load().baseline();
		config.load().migrate();
	}

	@Test
	void nonEmptySchemaWithInitOnMigrate() {
		createTestTable();
		Flylite flyway = flyway().locations(BASEDIR).baselineVersion("0").baselineOnMigrate(true).load();
		flyway.migrate();
		MigrationInfo[] migrationInfos = flyway.info().all();
		assertEquals(5, migrationInfos.length);
		assertEquals(MigrationType.BASELINE, migrationInfos[0].getType());
		assertEquals("0", String.valueOf(migrationInfos[0].getVersion()));
		assertEquals("2.0", version(flyway).toString());
	}

	@Test
	void nonEmptySchemaWithInitOnMigrateHighVersion() {
		createTestTable();
		Flylite flyway = flyway().locations(BASEDIR)
			.baselineOnMigrate(true)
			.baselineVersion(MigrationVersion.fromVersion("99"))
			.load();
		flyway.migrate();
		MigrationInfo[] migrationInfos = flyway.info().all();
		assertEquals(5, migrationInfos.length);
		assertEquals(MigrationType.SQL, migrationInfos[0].getType());
		assertEquals("1", String.valueOf(migrationInfos[0].getVersion()));
		assertEquals(MigrationState.BELOW_BASELINE, migrationInfos[0].getState());
		MigrationInfo migrationInfo = flyway.info().current();
		assertNotNull(migrationInfo);
		assertEquals(MigrationType.BASELINE, migrationInfo.getType());
		assertEquals("99", String.valueOf(migrationInfo.getVersion()));
	}

	@Test
	void semicolonWithinStringLiteral() {
		Flylite flyway = flyway().locations(MIGRATIONDIR + "/semicolon").load();
		flyway.migrate();
		assertEquals("1.1", version(flyway).toString());
		assertEquals("Populate table", flyway.info().current().getDescription());
		assertEquals("Mr. Semicolon+Linebreak;\nanother line",
				jdbc.string("select name from test_user where name like '%line'"));
	}

	@Test
	void subDir() {
		assertEquals(3, flyway().locations(MIGRATIONDIR + "/subdir").load().migrate().migrationsExecuted);
	}

	@Test
	void comment() {
		assertEquals(1, flyway().locations(MIGRATIONDIR + "/comment").load().migrate().migrationsExecuted);
	}

	@Test
	void outOfOrderMultipleRankIncrease() {
		flyway().locations(BASEDIR).load().migrate();
		Flylite flyway = flyway().locations(BASEDIR, MIGRATIONDIR + "/outoforder").outOfOrder(true).load();
		flyway.migrate();
		MigrationInfo[] all = flyway.info().all();
		assertEquals(MigrationState.OUT_OF_ORDER, all[all.length - 1].getState());
	}

	void createTestTable() {
		jdbc.execute("CREATE TABLE t1 (\n  name VARCHAR(25) NOT NULL,\n  PRIMARY KEY(name))");
	}

}
