package com.snaphop.flylite.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.snaphop.flylite.Flylite;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.MigrationInfo;
import com.snaphop.flylite.api.MigrationType;
import com.snaphop.flylite.api.migration.BaseJavaMigration;
import com.snaphop.flylite.api.migration.Context;

/**
 * PostgreSQL specific end to end tests.
 */
class PostgresTest {

	@TempDir
	Path dir;

	@Test
	void multipleSchemasAreCreatedMigratedAndDropped() {
		var database = Db.POSTGRES.create(dir);
		var migrations = new Migrations(dir.resolve("m")) //
			.add("V1__Tables.sql", """
					CREATE TABLE ${flyway:defaultSchema}.app_table (id INT);
					CREATE TABLE audit.audit_table (id INT);
					CREATE TABLE unqualified (id INT);
					""");
		var config = database.configure().locations(migrations.location()).schemas("app", "audit").cleanDisabled(false);
		Flylite flylite = config.load();
		assertEquals(1, flylite.migrate().migrationsExecuted);

		var jdbc = database.jdbc();
		assertEquals(1, jdbc.count("SELECT COUNT(*) FROM app.flyway_schema_history WHERE type = 'SCHEMA'"));
		assertEquals("\"app\",\"audit\"",
				jdbc.string("SELECT script FROM app.flyway_schema_history WHERE type = 'SCHEMA'"));
		// the default schema is the search path while migrating
		assertEquals(1, jdbc.count(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'app' AND table_name = 'unqualified'"));
		MigrationInfo[] all = flylite.info().all();
		assertEquals(MigrationType.SCHEMA, all[0].getType());
		flylite.validate();

		var clean = flylite.clean();
		assertEquals(List.of("app", "audit"), clean.schemasDropped);
		assertEquals(0,
				jdbc.count("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name IN ('app', 'audit')"));

		assertEquals(1, config.load().migrate().migrationsExecuted);
	}

	@Test
	void existingSchemasAreCleanedNotDropped() {
		var database = Db.POSTGRES.create(dir);
		database.jdbc().execute("CREATE SCHEMA existing");
		var migrations = new Migrations(dir.resolve("m")).add("V1__T.sql", "CREATE TABLE t (id INT);\n");
		var config = database.configure()
			.locations(migrations.location())
			.defaultSchema("existing")
			.cleanDisabled(false);
		config.load().migrate();
		var clean = config.load().clean();
		assertEquals(List.of("existing"), clean.schemasCleaned);
		assertTrue(clean.schemasDropped.isEmpty());
		assertEquals(1, database.jdbc()
			.count("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'existing'"));

		var unknown = database.configure().schemas("does_not_exist").cleanDisabled(false).createSchemas(false).load();
		assertTrue(unknown.clean().warnings.get(0).contains("Unable to clean unknown schema"));
	}

	@Test
	void cleanDropsAllObjectTypes() {
		var database = Db.POSTGRES.create(dir);
		var migrations = new Migrations(dir.resolve("m")).add("V1__Everything.sql", """
				CREATE EXTENSION IF NOT EXISTS pg_trgm;
				CREATE TYPE mood AS ENUM ('sad', 'ok', 'happy');
				CREATE DOMAIN positive_int AS INT CHECK (VALUE > 0);
				CREATE TYPE pair AS (a INT, b INT);
				CREATE COLLATION german (provider = icu, locale = 'de-DE');
				CREATE SEQUENCE seq;
				CREATE TABLE parent (id positive_int PRIMARY KEY, m mood, name TEXT COLLATE german);
				CREATE TABLE child (extra INT) INHERITS (parent);
				CREATE VIEW parent_view AS SELECT * FROM parent;
				CREATE MATERIALIZED VIEW parent_mview AS SELECT * FROM parent;
				CREATE FUNCTION add_one(i INT) RETURNS INT AS $$ SELECT i + 1 $$ LANGUAGE sql;
				CREATE PROCEDURE do_nothing() LANGUAGE plpgsql AS $$ BEGIN END $$;
				CREATE AGGREGATE my_sum(INT) (SFUNC = int4pl, STYPE = INT);
				""");
		var config = database.configure().locations(migrations.location()).cleanDisabled(false);
		config.load().migrate();
		config.load().clean();
		assertEquals(0, database.jdbc()
			.count("SELECT COUNT(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'public'"));
		// everything can be created again
		assertEquals(1, config.load().migrate().migrationsExecuted);
	}

	@Test
	void postgresSqlSyntax() {
		var database = Db.POSTGRES.create(dir);
		var migrations = new Migrations(dir.resolve("m")) //
			.add("V1__Copy.sql", """
					CREATE TABLE copy_test (id INT, name TEXT);
					COPY copy_test (id, name) FROM STDIN;
					1	one
					2	two
					\\.
					INSERT INTO copy_test VALUES (3, 'three');
					""") //
			.add("V2__Begin_atomic_with_case.sql", """
					CREATE FUNCTION sign_of(x INT) RETURNS INT LANGUAGE SQL
					BEGIN ATOMIC
					  SELECT CASE WHEN x > 0 THEN 1
					              ELSE CASE WHEN x < 0 THEN -1 ELSE 0 END
					         END;
					END;
					CREATE TABLE after_atomic (id INT);
					""") //
			.add("V3__Dollar_quotes.sql", """
					CREATE FUNCTION quoted() RETURNS TEXT AS $body$
					BEGIN
					  RETURN $$it's ; quoted$$;
					END;
					$body$ LANGUAGE plpgsql;
					DO $$ BEGIN RAISE NOTICE 'a notice'; END $$;
					""") //
			.add("V4__Index_concurrently.sql", "CREATE INDEX CONCURRENTLY copy_test_id ON copy_test (id);\n") //
			.add("V5__Mixed.sql", """
					CREATE TABLE mixed (id INT);
					CREATE INDEX CONCURRENTLY mixed_id ON mixed (id);
					""");
		// CREATE INDEX CONCURRENTLY waits for every open transaction, including the one
		// holding the transactional advisory lock, so like Flyway it needs a session
		// lock.
		var config = database.configure().locations(migrations.location()).postgresqlTransactionalLock(false);
		var e = assertThrows(FlyliteException.class, () -> config.load().migrate());
		assertTrue(e.getMessage().contains("mixed"), e.getMessage());

		assertEquals(1, config.mixed(true).load().migrate().migrationsExecuted);
		var jdbc = database.jdbc();
		assertEquals(3, jdbc.count("SELECT COUNT(*) FROM copy_test"));
		assertEquals(List.of("-1", "0", "1"),
				jdbc.strings("SELECT sign_of(x) FROM (VALUES (-5), (0), (5)) v(x) ORDER BY 1"));
		assertTrue(jdbc.tableExists("after_atomic"));
		assertEquals("it's ; quoted", jdbc.string("SELECT quoted()"));
		assertEquals(1, jdbc.count("SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'copy_test_id'"));
		assertEquals(1, jdbc.count("SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'mixed_id'"));
		assertFalse(config.load().info().all()[3].getState().isFailed());
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void concurrentMigrationsAreSerializedByAdvisoryLock(boolean transactionalLock) throws Exception {
		var database = Db.POSTGRES.create(dir);
		var migrations = new Migrations(dir.resolve("m")).standard();
		for (int i = 3; i < 10; i++) {
			migrations.add("V" + i + "__Slow_" + i + ".sql",
					"CREATE TABLE slow_" + i + " (id INT);\nSELECT pg_sleep(0.05);\n");
		}
		var config = database.configure()
			.locations(migrations.location())
			.postgresqlTransactionalLock(transactionalLock);
		ExecutorService executor = Executors.newFixedThreadPool(4);
		try {
			List<Future<Integer>> futures = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				Callable<Integer> task = () -> config.load().migrate().migrationsExecuted;
				futures.add(executor.submit(task));
			}
			int total = 0;
			for (Future<Integer> f : futures) {
				total += f.get();
			}
			assertEquals(11, total);
		}
		finally {
			executor.shutdownNow();
		}
		config.load().validate();
		assertEquals(11, database.jdbc().count("SELECT COUNT(*) FROM flyway_schema_history"));
	}

	public static final class V1__Capture_application_name extends BaseJavaMigration {

		@Override
		public void migrate(Context context) throws Exception {
			try (Statement s = context.getConnection().createStatement();
					ResultSet rs = s.executeQuery("SELECT current_setting('application_name')")) {
				rs.next();
				String name = rs.getString(1);
				s.execute("CREATE TABLE app_name AS SELECT '" + name + "'::text AS name");
			}
		}

	}

	@Test
	void applicationNameAndJdbcProperties() {
		var database = Db.POSTGRES.create(dir);
		database.configure().javaMigrations(new V1__Capture_application_name()).load().migrate();
		assertEquals("Flylite", database.jdbc().string("SELECT name FROM app_name"));

		var other = Db.POSTGRES.create(dir);
		other.configure()
			.javaMigrations(new V1__Capture_application_name())
			.jdbcProperties(Map.of("ApplicationName", "custom"))
			.load()
			.migrate();
		assertEquals("custom", other.jdbc().string("SELECT name FROM app_name"));
	}

	@Test
	void tablespaceAndConnectRetries() {
		var database = Db.POSTGRES.create(dir);
		var migrations = new Migrations(dir.resolve("m")).add("V1__T.sql", "CREATE TABLE t (id INT);\n");
		database.configure().locations(migrations.location()).tablespace("pg_default").load().migrate();
		assertEquals(1, database.jdbc().count("SELECT COUNT(*) FROM flyway_schema_history"));

		var bad = Flylite.configure()
			.dataSource("jdbc:postgresql://localhost:1/nothing", "nobody", "secret")
			.connectRetries(1)
			.connectRetriesInterval(1)
			.load();
		var e = assertThrows(FlyliteException.class, bad::migrate);
		assertTrue(e.getMessage().contains("Unable to obtain connection"), e.getMessage());
		assertFalse(e.getMessage().contains("secret"));
		assertThrows(FlyliteException.class, () -> Flylite.configure().connectRetries(-1));
		assertThrows(FlyliteException.class, () -> Flylite.configure().connectRetriesInterval(-1));
	}

	@Test
	void searchPathAndCurrentUserPlaceholders() {
		var database = Db.POSTGRES.create(dir);
		database.jdbc().execute("CREATE SCHEMA other");
		var migrations = new Migrations(dir.resolve("m")).add("V1__T.sql", """
				CREATE TABLE info AS SELECT '${flyway:user}'::text AS u, '${flyway:database}'::text AS d,
				  '${flyway:defaultSchema}'::text AS s, current_schema() AS cs;
				""");
		database.configure().locations(migrations.location()).defaultSchema("other").load().migrate();
		var jdbc = database.jdbc();
		assertEquals(database.user(), jdbc.string("SELECT u FROM other.info"));
		assertEquals("other", jdbc.string("SELECT s FROM other.info"));
		assertEquals("other", jdbc.string("SELECT cs FROM other.info"));
		assertNotNull(jdbc.string("SELECT d FROM other.info"));
	}

}
