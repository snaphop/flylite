package com.snaphop.flylite.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.snaphop.flylite.Flylite;
import com.snaphop.flylite.api.ErrorCode;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.MigrationInfo;
import com.snaphop.flylite.api.MigrationState;
import com.snaphop.flylite.api.MigrationType;
import com.snaphop.flylite.api.exception.FlyliteValidateException;
import com.snaphop.flylite.api.output.ValidateResult;

/**
 * End to end tests of validate and repair.
 */
class ValidateRepairTest {

	@TempDir
	Path dir;

	@ParameterizedTest
	@EnumSource(Db.class)
	void changedMigrationFailsValidationAndRepairRealignsIt(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).standard();
		Flylite flylite = database.configure().locations(migrations.location()).load();
		flylite.migrate();
		ValidateResult ok = flylite.validateWithResult();
		assertTrue(ok.validationSuccessful);
		assertEquals(4, ok.validateCount);
		assertEquals("validate", ok.operation);

		migrations.add("V1_1__Populate_person.sql", "INSERT INTO person (id, name) VALUES (1, 'changed');\n");
		migrations.add("V2__Create_address.sql", "-- just a comment change\nSELECT 1;\n");

		var e = assertThrows(FlyliteValidateException.class, flylite::validate);
		assertTrue(e.getMessage().contains("Migration checksum mismatch for migration version 1.1"), e.getMessage());
		ValidateResult failed = flylite.validateWithResult();
		assertFalse(failed.validationSuccessful);
		assertEquals(List.of(ErrorCode.CHECKSUM_MISMATCH, ErrorCode.CHECKSUM_MISMATCH),
				failed.invalidMigrations.stream().map(v -> v.errorDetails().errorCode).toList());
		assertThrows(FlyliteValidateException.class, flylite::migrate);

		var repair = flylite.repair();
		assertEquals(List.of("Aligned applied migration checksums"), repair.repairActions);
		assertEquals(List.of("1.1", "2"), repair.migrationsAligned.stream().map(r -> r.version()).toList());
		flylite.validate();
		assertEquals(0, flylite.migrate().migrationsExecuted);
		assertTrue(flylite.repair().repairActions.isEmpty());
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void renamedMigrationDescriptionIsRepaired(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).add("V1__Old_name.sql", "CREATE TABLE one (id INT);\n");
		Flylite flylite = database.configure().locations(migrations.location()).load();
		flylite.migrate();

		migrations.remove("V1__Old_name.sql").add("V1__New_name.sql", "CREATE TABLE one (id INT);\n");
		var result = flylite.validateWithResult();
		assertEquals(ErrorCode.DESCRIPTION_MISMATCH, result.invalidMigrations.get(0).errorDetails().errorCode);
		flylite.repair();
		flylite.validate();
		assertEquals("New name", flylite.info().all()[0].getDescription());
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void missingMigrationIsMarkedDeletedByRepair(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).standard();
		var config = database.configure().locations(migrations.location());
		config.load().migrate();

		migrations.remove("V1_1__Populate_person.sql").remove("R__Person_view.sql");
		var states = Arrays.stream(config.load().info().all()).map(MigrationInfo::getState).toList();
		assertTrue(states.contains(MigrationState.MISSING_SUCCESS), states.toString());
		var result = config.load().validateWithResult();
		assertEquals(
				List.of(ErrorCode.APPLIED_VERSIONED_MIGRATION_NOT_RESOLVED,
						ErrorCode.APPLIED_REPEATABLE_MIGRATION_NOT_RESOLVED),
				result.invalidMigrations.stream().map(v -> v.errorDetails().errorCode).toList());

		config.ignoreMigrationPatterns("*:missing").load().validate();

		var repair = config.ignoreMigrationPatterns("*:future").load().repair();
		assertEquals(List.of("Deleted missing migrations"), repair.repairActions);
		assertEquals(2, repair.migrationsDeleted.size());
		Flylite flylite = config.load();
		flylite.validate();
		assertEquals(2, database.jdbc().count("SELECT COUNT(*) FROM flyway_schema_history WHERE type = 'DELETE'"));
		assertTrue(Arrays.stream(flylite.info().all()).anyMatch(i -> i.getType() == MigrationType.DELETE));
		assertTrue(Arrays.stream(flylite.info().all()).anyMatch(i -> i.getState() == MigrationState.DELETED));
		assertEquals(0, flylite.migrate().migrationsExecuted);

		// the deleted migrations come back as pending when they reappear
		migrations.add("V1_1__Populate_person.sql", "INSERT INTO person (id, name) VALUES (3, 'back');\n");
		assertEquals(1, config.outOfOrder(true).load().migrate().migrationsExecuted);
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void futureMigrationsAreIgnoredByDefault(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).standard().remove("R__Person_view.sql");
		var config = database.configure().locations(migrations.location());
		config.load().migrate();

		var older = new Migrations(dir.resolve("older")).add("V1__Create_person.sql", """
				CREATE TABLE person (
				  id INT NOT NULL PRIMARY KEY,
				  name VARCHAR(100) NOT NULL
				);
				""");
		var olderConfig = database.configure().locations(older.location());
		var states = Arrays.stream(olderConfig.load().info().all()).map(MigrationInfo::getState).toList();
		assertTrue(states.contains(MigrationState.FUTURE_SUCCESS), states.toString());
		olderConfig.load().validate();
		assertEquals(0, olderConfig.load().migrate().migrationsExecuted);

		var strict = olderConfig.ignoreMigrationPatterns(new String[0]).load().validateWithResult();
		assertFalse(strict.validationSuccessful);
		assertEquals(ErrorCode.APPLIED_VERSIONED_MIGRATION_NOT_RESOLVED,
				strict.invalidMigrations.get(0).errorDetails().errorCode);
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void validateWithPendingAndMissingSchemaHistory(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).standard();
		var config = database.configure().locations(migrations.location());

		var pending = config.load().validateWithResult();
		assertFalse(pending.validationSuccessful);
		assertEquals(ErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED,
				pending.invalidMigrations.get(0).errorDetails().errorCode);
		config.ignoreMigrationPatterns("*:pending").load().validate();
		assertThrows(FlyliteException.class, () -> config.ignoreMigrationPatterns("bad"));
		assertThrows(FlyliteException.class, () -> config.ignoreMigrationPatterns("*:unknown"));
		assertThrows(FlyliteException.class, () -> config.ignoreMigrationPatterns("nope:pending"));
		config.ignoreMigrationPatterns("versioned:pending", "repeatable:pending").load().validate();

		config.ignoreMigrationPatterns("*:future").validateOnMigrate(false).load().migrate();
		config.load().validate();
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void repairWithoutSchemaHistoryDoesNothing(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).standard();
		var repair = database.configure().locations(migrations.location()).load().repair();
		assertTrue(repair.repairActions.isEmpty());
		assertEquals("repair", repair.operation);
	}

}
