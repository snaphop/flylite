package com.snaphop.flylite.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.snaphop.flylite.Flylite;
import com.snaphop.flylite.api.ErrorCode;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.MigrationInfo;
import com.snaphop.flylite.api.MigrationState;
import com.snaphop.flylite.api.MigrationType;
import com.snaphop.flylite.api.MigrationVersion;
import com.snaphop.flylite.api.exception.FlyliteValidateException;
import com.snaphop.flylite.api.output.MigrateResult;

/**
 * End to end tests of migrate against every supported database.
 */
class MigrateTest {

	@TempDir
	Path dir;

	@ParameterizedTest
	@EnumSource(Db.class)
	void migrateAppliesVersionedThenRepeatableMigrations(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).standard();
		Flylite flylite = database.configure().locations(migrations.location()).load();

		MigrateResult result = flylite.migrate();

		assertTrue(result.success);
		assertEquals(4, result.migrationsExecuted);
		assertNull(result.initialSchemaVersion);
		assertEquals("2", result.targetSchemaVersion);
		assertEquals(List.of("1", "1.1", "2", ""), result.migrations.stream().map(m -> m.version()).toList());
		assertEquals(List.of("Versioned", "Versioned", "Versioned", "Repeatable"),
				result.migrations.stream().map(m -> m.category()).toList());
		assertEquals("migrate", result.operation);

		var jdbc = database.jdbc();
		assertEquals(2, jdbc.count("SELECT COUNT(*) FROM person"));
		assertEquals("Main St", jdbc.string("SELECT street FROM person_view"));
		assertEquals("Mrs. 'Quote'", jdbc.string("SELECT name FROM person WHERE id = 2"));
		assertEquals(4, jdbc.count("SELECT COUNT(*) FROM flyway_schema_history WHERE success = " + bool(db, true)));

		var info = flylite.info();
		MigrationInfo current = info.current();
		assertNotNull(current);
		assertEquals(MigrationVersion.fromVersion("2"), current.getVersion());
		assertEquals(4, info.applied().length);
		assertEquals(0, info.pending().length);
		for (MigrationInfo mi : info.all()) {
			assertEquals(MigrationState.SUCCESS, mi.getState(), mi.getScript());
			assertEquals(MigrationType.SQL, mi.getType());
			assertNotNull(mi.getInstalledOn());
			assertNotNull(mi.getChecksum());
		}
		var infoResult = info.getInfoResult();
		assertEquals("2", infoResult.schemaVersion);
		assertEquals(4, infoResult.migrations.size());
		assertFalse(infoResult.allSchemasEmpty);

		MigrateResult second = flylite.migrate();
		assertEquals(0, second.migrationsExecuted);
		assertEquals("2", second.initialSchemaVersion);
		flylite.validate();
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void repeatableMigrationIsReappliedWhenItChanges(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).standard();
		Flylite flylite = database.configure().locations(migrations.location()).load();
		flylite.migrate();

		migrations.add("R__Person_view.sql", """
				DROP VIEW IF EXISTS person_view;
				CREATE VIEW person_view AS SELECT name FROM person;
				""");

		var states = Arrays.stream(flylite.info().all()).map(MigrationInfo::getState).toList();
		assertTrue(states.contains(MigrationState.OUTDATED), states.toString());
		assertEquals(1, flylite.migrate().migrationsExecuted);
		assertEquals(2, database.jdbc().count("SELECT COUNT(*) FROM person_view"));
		states = Arrays.stream(flylite.info().all()).map(MigrationInfo::getState).toList();
		assertTrue(states.contains(MigrationState.SUPERSEDED), states.toString());
		assertEquals(0, flylite.migrate().migrationsExecuted);
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void targetStopsAtVersion(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).standard().remove("R__Person_view.sql");
		var config = database.configure().locations(migrations.location());

		config.target("1.1").load().migrate();
		MigrationInfo current = config.load().info().current();
		assertNotNull(current);
		assertEquals("1.1", String.valueOf(current.getVersion()));
		var aboveTarget = Arrays.stream(config.load().info().all())
			.filter(i -> i.getState() == MigrationState.ABOVE_TARGET)
			.count();
		assertEquals(1, aboveTarget);

		config.target("current").load().migrate();
		assertEquals("1.1", String.valueOf(config.load().info().current().getVersion()));

		config.target(MigrationVersion.LATEST).load().migrate();
		assertEquals("2", String.valueOf(config.load().info().current().getVersion()));
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void lateMigrationFailsValidationUnlessOutOfOrder(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).add("V1__One.sql", "CREATE TABLE one (id INT);")
			.add("V3__Three.sql", "CREATE TABLE three (id INT);");
		var config = database.configure().locations(migrations.location());
		config.load().migrate();

		migrations.add("V2__Two.sql", "CREATE TABLE two (id INT);");
		var e = assertThrows(FlyliteValidateException.class, () -> config.load().migrate());
		assertEquals(ErrorCode.VALIDATE_ERROR, e.getErrorCode());
		assertTrue(e.getMessage().contains("Detected resolved migration not applied to database: 2"), e.getMessage());
		var validate = config.load().validateWithResult();
		assertEquals(ErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED,
				validate.invalidMigrations.get(0).errorDetails().errorCode);
		assertEquals(MigrationState.IGNORED, config.load().info().all()[2].getState());

		// ignoring ignored migrations allows migrate but V2 is still not applied
		assertEquals(0, config.ignoreMigrationPatterns("*:ignored").load().migrate().migrationsExecuted);

		assertEquals(1,
				config.ignoreMigrationPatterns("*:future").outOfOrder(true).load().migrate().migrationsExecuted);
		MigrationInfo[] all = config.load().info().all();
		assertEquals(MigrationState.OUT_OF_ORDER, all[all.length - 1].getState());
		assertTrue(database.jdbc().tableExists("two"));
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void failedMigrationIsRolledBack(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).add("V1__One.sql", "CREATE TABLE one (id INT);")
			.add("V2__Fails.sql", """
					CREATE TABLE before_the_error (id INT);
					INSERT INTO one (id) VALUES (1);
					THIS IS NOT VALID SQL;
					""");
		Flylite flylite = database.configure().locations(migrations.location()).load();

		var e = assertThrows(FlyliteException.class, flylite::migrate);
		assertTrue(e.getMessage().contains("THIS IS NOT VALID SQL"), e.getMessage());
		assertTrue(e.getMessage().contains("Line       : 3"), e.getMessage());

		// both databases support DDL transactions so the failed migration leaves no trace
		assertFalse(database.jdbc().tableExists("before_the_error"));
		assertEquals(0, database.jdbc().count("SELECT COUNT(*) FROM one"));
		MigrationInfo current = flylite.info().current();
		assertNotNull(current);
		assertEquals("1", String.valueOf(current.getVersion()));
		assertEquals(1, flylite.info().pending().length);
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void groupRollsBackAllPendingMigrations(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).add("V1__One.sql", "CREATE TABLE one (id INT);")
			.add("V2__Two.sql", "CREATE TABLE two (id INT);")
			.add("V3__Fails.sql", "THIS IS NOT VALID SQL;");
		Flylite flylite = database.configure().locations(migrations.location()).group(true).load();

		assertThrows(FlyliteException.class, flylite::migrate);
		assertNull(flylite.info().current());
		assertEquals(3, flylite.info().pending().length);
		assertFalse(database.jdbc().tableExists("one"));
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void nonTransactionalScriptLeavesFailedEntry(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).add("V1__One.sql", "CREATE TABLE one (id INT);")
			.add("V2__Fails.sql", "CREATE TABLE two (id INT);\nTHIS IS NOT VALID SQL;\n")
			.add("V2__Fails.sql.conf", "executeInTransaction=false\n");
		var config = database.configure().locations(migrations.location());
		if (database.isPostgres()) {
			// With the default transactional advisory lock the schema history changes are
			// rolled back together with the lock transaction (same as upstream Flyway),
			// so
			// the failed migration is not recorded.
			assertThrows(FlyliteException.class, () -> config.load().migrate());
			assertTrue(database.jdbc().tableExists("two"));
			MigrationInfo current = config.load().info().current();
			assertNotNull(current);
			assertEquals("1", String.valueOf(current.getVersion()));
			database.jdbc().execute("DROP TABLE two");
			config.postgresqlTransactionalLock(false);
		}
		Flylite flylite = config.load();

		assertThrows(FlyliteException.class, flylite::migrate);
		assertTrue(database.jdbc().tableExists("two"));
		MigrationInfo current = flylite.info().current();
		assertNotNull(current);
		assertEquals(MigrationState.FAILED, current.getState());

		var validate = flylite.validateWithResult();
		assertFalse(validate.validationSuccessful);
		assertEquals(ErrorCode.FAILED_VERSIONED_MIGRATION, validate.invalidMigrations.get(0).errorDetails().errorCode);
		var e = assertThrows(FlyliteException.class, flylite::migrate);
		assertTrue(e.getMessage().contains("failed migration"), e.getMessage());

		var repair = flylite.repair();
		assertEquals(List.of("Removed failed migrations"), repair.repairActions);
		assertEquals(1, repair.migrationsRemoved.size());

		database.jdbc().execute("DROP TABLE two");
		migrations.add("V2__Fails.sql", "CREATE TABLE two (id INT);\n");
		assertEquals(1, flylite.migrate().migrationsExecuted);
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void unknownScriptConfigurationIsRejected(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).add("V1__One.sql", "CREATE TABLE one (id INT);")
			.add("V1__One.sql.conf", "shouldExecute=true\n");
		var e = assertThrows(FlyliteException.class,
				() -> database.configure().locations(migrations.location()).load().migrate());
		assertTrue(e.getMessage().contains("shouldExecute"), e.getMessage());
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void placeholdersAreReplaced(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")) //
			.add("V1__Placeholders.sql",
					"""
							CREATE TABLE ${tableName} (name VARCHAR(100), history VARCHAR(100), file VARCHAR(100));
							INSERT INTO ${tableName} (name, history, file) VALUES ('${value}', '${flyway:table}', '${flyway:filename}');
							""") //
			.add("V2__Custom_placeholders.sql", """
					INSERT INTO placeholders (name) VALUES ('#[value]');
					INSERT INTO placeholders (name) VALUES ('FP__value__');
					""");
		Flylite flylite = database.configure()
			.locations(migrations.location())
			.placeholders(Map.of("tableName", "placeholders", "value", "replaced"))
			.load();
		flylite.migrate();
		var jdbc = database.jdbc();
		assertEquals("replaced", jdbc.string("SELECT name FROM placeholders WHERE history IS NOT NULL"));
		assertEquals("flyway_schema_history",
				jdbc.string("SELECT history FROM placeholders WHERE history IS NOT NULL"));
		assertEquals("V1__Placeholders.sql", jdbc.string("SELECT file FROM placeholders WHERE history IS NOT NULL"));
		assertEquals(List.of("#[value]", "FP__value__"),
				jdbc.strings("SELECT name FROM placeholders WHERE history IS NULL ORDER BY name"));

		var other = db.create(dir);
		var custom = new Migrations(dir.resolve("custom")) //
			.add("V1__Placeholders.sql", """
					CREATE TABLE #[tableName] (name VARCHAR(100));
					INSERT INTO #[tableName] (name) VALUES ('${value}');
					INSERT INTO #[tableName] (name) VALUES ('#[value]');
					INSERT INTO #[tableName] (name) VALUES ('FP|value|');
					""");
		other.configure()
			.locations(custom.location())
			.placeholders(Map.of("tableName", "placeholders", "value", "replaced"))
			.placeholderPrefix("#[")
			.placeholderSuffix("]")
			.placeholderSeparator("|")
			.load()
			.migrate();
		assertEquals(List.of("${value}", "FP|value|", "replaced"),
				other.jdbc().strings("SELECT name FROM placeholders ORDER BY name"));

		var e = assertThrows(FlyliteException.class,
				() -> db.create(dir)
					.configure()
					.locations(migrations.location())
					.placeholders(Map.of("tableName", "placeholders"))
					.load()
					.migrate());
		assertTrue(e.getMessage().contains("No value provided for placeholder"), e.getMessage());

		var noReplacement = db.create(dir);
		new Migrations(dir.resolve("raw")).add("V1__Raw.sql",
				"CREATE TABLE raw (v VARCHAR(10));\nINSERT INTO raw VALUES ('${x}');\n");
		noReplacement.configure()
			.locations("filesystem:" + dir.resolve("raw"))
			.placeholderReplacement(false)
			.load()
			.migrate();
		assertEquals("${x}", noReplacement.jdbc().string("SELECT v FROM raw"));

		assertThrows(FlyliteException.class,
				() -> db.create(dir)
					.configure()
					.locations(migrations.location())
					.placeholders(Map.of("flyway:reserved", "x"))
					.load()
					.migrate());
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void encoding(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")) //
			.add("V1__Encoding.sql", "CREATE TABLE enc (v VARCHAR(10));\nINSERT INTO enc VALUES ('café');\n",
					StandardCharsets.ISO_8859_1);
		database.configure().locations(migrations.location()).encoding("ISO-8859-1").load().migrate();
		assertEquals("café", database.jdbc().string("SELECT v FROM enc"));

		var perScript = db.create(dir);
		var m2 = new Migrations(dir.resolve("m2")) //
			.add("V1__Encoding.sql", "CREATE TABLE enc (v VARCHAR(10));\nINSERT INTO enc VALUES ('café');\n",
					StandardCharsets.ISO_8859_1)
			.add("V1__Encoding.sql.conf", "encoding=ISO-8859-1\n");
		perScript.configure().locations(m2.location()).load().migrate();
		assertEquals("café", perScript.jdbc().string("SELECT v FROM enc"));
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void customTableInstalledByAndInitSql(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).standard()
			.add("V3__Uses_init_sql.sql", "CREATE TABLE init_copy AS SELECT * FROM init_marker;");
		database.configure()
			.locations(migrations.location())
			.table("my_history")
			.installedBy("deployer")
			// a temp table per connection does not make the schema non-empty
			.initSql("CREATE TEMP TABLE IF NOT EXISTS init_marker AS SELECT 1 AS id")
			.load()
			.migrate();
		var jdbc = database.jdbc();
		assertEquals(5, jdbc.count("SELECT COUNT(*) FROM my_history WHERE installed_by = 'deployer'"));
		assertFalse(jdbc.tableExists("flyway_schema_history"));
		assertEquals(1, jdbc.count("SELECT COUNT(*) FROM init_copy"));
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void classpathLocationsAndJavaMigrations(Db db) {
		var database = db.create(dir);
		Flylite flylite = database.configure()
			.locations("classpath:db/classpath", "db/javamig")
			.javaMigrations(new FixedJavaMigration())
			.load();
		MigrateResult result = flylite.migrate();
		assertEquals(5, result.migrationsExecuted);
		var jdbc = database.jdbc();
		assertEquals(List.of("V3__Java_insert", "fixed 5"),
				jdbc.strings("SELECT name FROM classpath_test ORDER BY id"));
		var types = Arrays.stream(flylite.info().all()).map(MigrationInfo::getType).toList();
		assertEquals(List.of(MigrationType.SQL, MigrationType.SQL, MigrationType.JDBC, MigrationType.JDBC,
				MigrationType.JDBC), types);
		assertEquals("Java insert", flylite.info().all()[2].getDescription());
		flylite.validate();
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void namingAndLocationErrors(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).add("V1__One.sql", "CREATE TABLE one (id INT);")
			.add("X1__Not_a_migration.sql", "CREATE TABLE x (id INT);")
			.add("V1_0__Duplicate.sql", "CREATE TABLE dup (id INT);");

		var duplicate = assertThrows(FlyliteException.class,
				() -> database.configure().locations(migrations.location()).load().migrate());
		assertEquals(ErrorCode.DUPLICATE_VERSIONED_MIGRATION, duplicate.getErrorCode());

		migrations.remove("V1_0__Duplicate.sql");
		var naming = assertThrows(FlyliteException.class,
				() -> database.configure()
					.locations(migrations.location())
					.validateMigrationNaming(true)
					.load()
					.migrate());
		assertTrue(naming.getMessage().contains("X1__Not_a_migration.sql"), naming.getMessage());

		assertEquals(1, database.configure().locations(migrations.location()).load().migrate().migrationsExecuted);

		assertThrows(FlyliteException.class,
				() -> database.configure()
					.locations("filesystem:" + dir.resolve("does-not-exist"), "classpath:does/not/exist")
					.failOnMissingLocations(true)
					.load()
					.migrate());
		assertEquals(0,
				database.configure()
					.locations("filesystem:" + dir.resolve("does-not-exist"), "classpath:does/not/exist")
					.load()
					.migrate().migrationsExecuted);
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void dataSourceConfiguration(Db db) throws Exception {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).add("V1__One.sql", "CREATE TABLE one (id INT);");
		var e = assertThrows(FlyliteException.class, () -> Flylite.configure().load().migrate());
		assertEquals(ErrorCode.CONFIGURATION, e.getErrorCode());

		var driver = db == Db.POSTGRES ? "org.postgresql.Driver" : "org.sqlite.JDBC";
		var flylite = database.configure().driver(driver).locations(migrations.location()).load();
		assertEquals(1, flylite.migrate().migrationsExecuted);

		var badDriver = assertThrows(FlyliteException.class,
				() -> database.configure().driver("no.such.Driver").locations(migrations.location()).load().migrate());
		assertEquals(ErrorCode.JDBC_DRIVER, badDriver.getErrorCode());

		var dataSource = new SimpleDataSource(database);
		var viaDataSource = Flylite.configure().dataSource(dataSource).locations(migrations.location()).load();
		assertEquals(0, viaDataSource.migrate().migrationsExecuted);
		assertEquals(dataSource, viaDataSource.getConfiguration().getDataSource());
	}

	private static String bool(Db db, boolean value) {
		return db == Db.SQLITE ? (value ? "1" : "0") : String.valueOf(value);
	}

}
