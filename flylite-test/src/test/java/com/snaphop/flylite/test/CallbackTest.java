package com.snaphop.flylite.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.snaphop.flylite.Flylite;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.callback.BaseCallback;
import com.snaphop.flylite.api.callback.Context;
import com.snaphop.flylite.api.callback.Event;
import com.snaphop.flylite.api.output.MigrateResult;

/**
 * End to end tests of SQL and Java callbacks.
 */
class CallbackTest {

	@TempDir
	Path dir;

	static final class Recorder extends BaseCallback {

		final List<String> events = new ArrayList<>();

		final List<Object> results = new ArrayList<>();

		@Override
		public void handle(Event event, Context context) {
			assertNotNull(context.getConnection());
			assertNotNull(context.getConfiguration());
			var info = context.getMigrationInfo();
			events.add(event.getId() + (info == null ? "" : ":" + info.getScript()));
			var result = context.getOperationResult();
			if (result != null) {
				results.add(result);
			}
		}

	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void callbacksFireInOrder(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).add("V1__One.sql", "CREATE TABLE one (id INT);\n")
			.add("R__Rep.sql", "DROP VIEW IF EXISTS rep; CREATE VIEW rep AS SELECT 1 AS id;\n")
			.add("beforeMigrate.sql", "CREATE TABLE IF NOT EXISTS callback_log (event VARCHAR(100));\n")
			.add("beforeEachMigrate.sql", "INSERT INTO callback_log (event) VALUES ('beforeEachMigrate');\n")
			.add("afterEachMigrate__Second.sql", "INSERT INTO callback_log (event) VALUES ('afterEachMigrate');\n")
			.add("afterMigrate.sql", "INSERT INTO callback_log (event) VALUES ('${flyway:table}');\n")
			.add("afterMigrateApplied.sql", "INSERT INTO callback_log (event) VALUES ('applied');\n");
		var recorder = new Recorder();
		Flylite flylite = database.configure().locations(migrations.location()).callbacks(recorder).load();
		MigrateResult result = flylite.migrate();
		assertEquals(2, result.migrationsExecuted);

		assertEquals(List.of("beforeEachMigrate", "afterEachMigrate", "beforeEachMigrate", "afterEachMigrate",
				"applied", "flyway_schema_history"), database.jdbc().strings("SELECT event FROM callback_log"));

		assertEquals(List.of("beforeValidate", "afterValidate", "createSchema", "beforeMigrate",
				"beforeEachMigrate:V1__One.sql", "afterEachMigrate:V1__One.sql", "afterVersioned", "beforeRepeatables",
				"beforeEachMigrate:R__Rep.sql", "afterEachMigrate:R__Rep.sql", "afterMigrateApplied", "afterMigrate",
				"afterMigrateOperationFinish"), recorder.events);
		assertEquals(List.of(result), recorder.results);

		recorder.events.clear();
		flylite.info();
		flylite.validate();
		flylite.repair();
		assertEquals(
				List.of("beforeInfo", "afterInfo", "afterInfoOperationFinish", "beforeValidate", "afterValidate",
						"afterValidateOperationFinish", "beforeRepair", "afterRepair", "afterRepairOperationFinish"),
				recorder.events);

		recorder.events.clear();
		db.create(dir).configure().locations(migrations.location()).callbacks(recorder).load().baseline();
		assertEquals(List.of("createSchema", "beforeBaseline", "afterBaseline", "afterBaselineOperationFinish"),
				recorder.events);

		recorder.events.clear();
		database.configure().locations(migrations.location()).callbacks(recorder).cleanDisabled(false).load().clean();
		assertEquals(List.of("beforeClean", "afterClean", "afterCleanOperationFinish"), recorder.events);
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void errorCallbacksAndSkippingDefaultCallbacks(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).add("V1__Fails.sql", "THIS IS NOT VALID SQL;\n")
			.add("beforeMigrate.sql", "THIS WOULD FAIL TOO;\n");
		var recorder = new Recorder();
		var config = database.configure().locations(migrations.location()).callbacks(recorder);

		var e = assertThrows(FlyliteException.class, () -> config.load().migrate());
		assertTrue(e.getMessage().contains("beforeMigrate"), e.getMessage());

		recorder.events.clear();
		assertThrows(FlyliteException.class, () -> config.skipDefaultCallbacks(true).load().migrate());
		assertTrue(recorder.events.contains("afterEachMigrateError:V1__Fails.sql"), recorder.events.toString());
		assertTrue(recorder.events.contains("afterMigrateError"), recorder.events.toString());
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void failingJavaCallbackIsReported(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).add("V1__One.sql", "CREATE TABLE one (id INT);\n");
		var failing = new BaseCallback() {
			@Override
			public boolean supports(Event event, Context context) {
				return event == Event.BEFORE_MIGRATE;
			}

			@Override
			public void handle(Event event, Context context) {
				throw new IllegalStateException("boom");
			}
		};
		var e = assertThrows(FlyliteException.class,
				() -> database.configure().locations(migrations.location()).callbacks(failing).load().migrate());
		assertTrue(e.getMessage().contains("Error while executing beforeMigrate callback: boom"), e.getMessage());
	}

}
