package com.snaphop.flylite.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.snaphop.flylite.Flylite;
import com.snaphop.flylite.api.ErrorCode;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.MigrationInfo;
import com.snaphop.flylite.api.MigrationState;
import com.snaphop.flylite.api.MigrationType;

/**
 * End to end tests of baseline and clean.
 */
class BaselineCleanTest {

	@TempDir
	Path dir;

	@ParameterizedTest
	@EnumSource(Db.class)
	void nonEmptySchemaRequiresBaseline(Db db) {
		var database = db.create(dir);
		existingPerson(database);
		var migrations = new Migrations(dir.resolve("m")).standard();
		var config = database.configure().locations(migrations.location());

		var e = assertThrows(FlyliteException.class, () -> config.load().migrate());
		assertEquals(ErrorCode.NON_EMPTY_SCHEMA_WITHOUT_SCHEMA_HISTORY_TABLE, e.getErrorCode());

		Flylite flylite = config.baselineOnMigrate(true).baselineVersion("1.1").load();
		var result = flylite.migrate();
		assertEquals(2, result.migrationsExecuted);
		MigrationInfo[] all = flylite.info().all();
		assertEquals(MigrationState.BELOW_BASELINE, all[0].getState());
		assertEquals(MigrationState.BASELINE_IGNORED, all[1].getState());
		assertEquals(MigrationType.BASELINE, all[2].getType());
		assertEquals("<< Flyway Baseline >>", all[2].getDescription());
		assertEquals(MigrationState.BASELINE, all[2].getState());
		flylite.validate();
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void explicitBaseline(Db db) {
		var database = db.create(dir);
		existingPerson(database);
		var migrations = new Migrations(dir.resolve("m")).standard();
		var config = database.configure().locations(migrations.location()).baselineDescription("Existing database");

		var baseline = config.load().baseline();
		assertTrue(baseline.successfullyBaselined);
		assertEquals("1", baseline.baselineVersion);
		assertEquals("baseline", baseline.operation);
		// same baseline again is a no-op
		assertTrue(config.load().baseline().successfullyBaselined);
		// a different one is not
		assertThrows(FlyliteException.class, () -> config.baselineVersion("2").load().baseline());

		database.jdbc().execute("DELETE FROM person");
		Flylite flylite = config.baselineVersion("1").load();
		assertEquals(3, flylite.migrate().migrationsExecuted);
		MigrationInfo current = flylite.info().current();
		assertNotNull(current);
		assertEquals("2", String.valueOf(current.getVersion()));
		// the database is already baselined with the same version so baseline is a no-op
		assertTrue(flylite.baseline().successfullyBaselined);
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void baselineCreatesSchemaHistoryWithoutCreatingSchemas(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).standard();
		var result = database.configure().locations(migrations.location()).createSchemas(false).load().baseline();
		assertTrue(result.successfullyBaselined);
		assertTrue(database.jdbc().tableExists("flyway_schema_history"));
	}

	private static void existingPerson(Db.TestDatabase database) {
		database.jdbc().execute("CREATE TABLE person (id INT NOT NULL PRIMARY KEY, name VARCHAR(100) NOT NULL)");
		database.jdbc().execute("INSERT INTO person (id, name) VALUES (1, 'existing')");
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void cleanIsDisabledByDefault(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).standard();
		Flylite flylite = database.configure().locations(migrations.location()).load();
		flylite.migrate();
		var e = assertThrows(FlyliteException.class, flylite::clean);
		assertTrue(e.getMessage().contains("disabled"), e.getMessage());
		assertTrue(database.jdbc().tableExists("person"));
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void cleanDropsEverythingAndMigrateStartsOver(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).standard().add("V3__Trigger.sql", db == Db.POSTGRES ? """
				CREATE SEQUENCE person_seq;
				CREATE FUNCTION touch() RETURNS trigger AS $$
				BEGIN
				  NEW.name := upper(NEW.name);
				  RETURN NEW;
				END;
				$$ LANGUAGE plpgsql;
				CREATE TRIGGER person_touch BEFORE INSERT ON person FOR EACH ROW EXECUTE PROCEDURE touch();
				CREATE INDEX person_name_idx ON person (name);
				""" : """
				CREATE TRIGGER person_touch AFTER INSERT ON person
				BEGIN
				  UPDATE person SET name = upper(name) WHERE id = NEW.id;
				END;
				CREATE INDEX person_name_idx ON person (name);
				""");
		Flylite flylite = database.configure().locations(migrations.location()).cleanDisabled(false).load();
		flylite.migrate();
		database.jdbc().execute("INSERT INTO person (id, name) VALUES (10, 'lower')");
		assertEquals("LOWER", database.jdbc().string("SELECT name FROM person WHERE id = 10"));

		var clean = flylite.clean();
		assertEquals("clean", clean.operation);
		assertFalse(clean.schemasCleaned.isEmpty());
		var jdbc = database.jdbc();
		assertFalse(jdbc.tableExists("person"));
		assertFalse(jdbc.tableExists("flyway_schema_history"));
		assertTrue(flylite.info().getInfoResult().allSchemasEmpty);

		assertEquals(5, flylite.migrate().migrationsExecuted);
		flylite.validate();
	}

}
