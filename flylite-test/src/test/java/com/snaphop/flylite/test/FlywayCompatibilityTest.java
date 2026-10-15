package com.snaphop.flylite.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.snaphop.flylite.Flylite;
import com.snaphop.flylite.api.MigrationInfo;

/**
 * Databases migrated with Flyway 9.19.1 (what Flylite was forked from) can be migrated
 * with Flylite and the other way around. This checks the schema history table format,
 * migration types and checksums.
 */
class FlywayCompatibilityTest {

	@TempDir
	Path dir;

	private static Flyway flyway(Db.TestDatabase database, Migrations migrations) {
		return Flyway.configure()
			.dataSource(database.url(), database.user(), database.password())
			.locations(migrations.location())
			.load();
	}

	private static List<Integer> checksums(Flylite flylite) {
		return Arrays.stream(flylite.info().all()).map(MigrationInfo::getChecksum).toList();
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void flyliteContinuesWhereFlywayStopped(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).standard();
		var flyway = flyway(database, migrations);
		assertEquals(4, flyway.migrate().migrationsExecuted);

		Flylite flylite = database.configure().locations(migrations.location()).load();
		flylite.validate();
		assertEquals(0, flylite.migrate().migrationsExecuted);
		assertEquals(Arrays.stream(flyway.info().all()).map(i -> i.getChecksum()).toList(), checksums(flylite));

		migrations.add("V3__Flylite.sql", "CREATE TABLE flylite_table (id INT);\n");
		assertEquals(1, flylite.migrate().migrationsExecuted);
		flyway.validate();
		assertEquals(0, flyway.migrate().migrationsExecuted);
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void flywayContinuesWhereFlyliteStopped(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).standard();
		database.jdbc().execute("CREATE TABLE existing (id INT)");
		Flylite flylite = database.configure()
			.locations(migrations.location())
			.baselineOnMigrate(true)
			.baselineVersion("0")
			.load();
		assertEquals(4, flylite.migrate().migrationsExecuted);

		var flyway = flyway(database, migrations);
		flyway.validate();
		assertEquals(Arrays.stream(flyway.info().all()).map(i -> i.getChecksum()).toList(), checksums(flylite));
		assertEquals(Arrays.stream(flyway.info().all()).map(i -> i.getType().name()).toList(),
				Arrays.stream(flylite.info().all()).map(i -> i.getType().name()).toList());

		migrations.add("R__Person_view.sql",
				"DROP VIEW IF EXISTS person_view;\nCREATE VIEW person_view AS SELECT name FROM person;\n");
		assertEquals(1, flyway.migrate().migrationsExecuted);
		flylite.validate();
		assertEquals(0, flylite.migrate().migrationsExecuted);
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void repairedAndDeletedEntriesAreUnderstoodByBoth(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).standard();
		Flylite flylite = database.configure().locations(migrations.location()).load();
		flylite.migrate();
		migrations.remove("V1_1__Populate_person.sql");
		flylite.repair();

		var flyway = flyway(database, migrations);
		flyway.validate();
		migrations.add("V2__Create_address.sql", "-- changed\nSELECT 1;\n");
		flyway.repair();
		flylite.validate();
	}

}
