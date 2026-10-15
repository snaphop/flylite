/*
 * Ported from Flyway 4.2 (flyway-core/src/test/java/org/flywaydb/core/internal/dbsupport/postgresql/PostgreSQLMigrationMediumTest.java).
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.snaphop.flylite.Flylite;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.test.Db;

/**
 * Flyway's PostgreSQL tests.
 */
class UpstreamPostgreSQLMigrationTest extends UpstreamMigrationTestCase {

	static final String PG = MIGRATIONDIR + "/dbsupport/postgresql/sql/";

	@Override
	Db db() {
		return Db.POSTGRES;
	}

	/**
	 * Migrates, cleans and migrates again: running migrate again on an unclean database
	 * triggers duplicate object exceptions.
	 * @param location the location under the postgresql folder.
	 * @return flylite.
	 */
	Flylite migrateCleanMigrate(String location) {
		Flylite flyway = flyway().locations(PG + location).load();
		flyway.migrate();
		flyway.clean();
		flyway.migrate();
		return flyway;
	}

	@Test
	void type() {
		migrateCleanMigrate("type");
	}

	@Test
	void baseType() {
		migrateCleanMigrate("basetype");
	}

	@Test
	void vacuum() {
		var e = assertThrows(FlyliteException.class, () -> flyway().locations(PG + "vacuum").load().migrate());
		assertTrue(e.getMessage().contains("non-transactional"), e.getMessage());
		flyway().locations(PG + "vacuum").mixed(true).load().migrate();
	}

	@Test
	void index() {
		flyway().locations(PG + "index").mixed(true).postgresqlTransactionalLock(false).load().migrate();
	}

	@Test
	void cleanUnknown() {
		flyway().schemas("non-existant").createSchemas(false).load().clean();
	}

	@Test
	void storedProcedure() {
		Flylite flyway = flyway().locations(PG + "procedure").load();
		flyway.migrate();
		assertEquals("Hello", jdbc.string("SELECT value FROM test_data"));
		flyway.clean();
		flyway.migrate();
	}

	@Test
	void function() {
		migrateCleanMigrate("function");
	}

	@Test
	void trigger() {
		Flylite flyway = flyway().locations(PG + "trigger").load();
		flyway.migrate();
		assertEquals(10, jdbc.count("SELECT count(*) FROM test4"));
		flyway.clean();
		flyway.migrate();
	}

	@Test
	void view() {
		Flylite flyway = flyway().locations(PG + "view").load();
		flyway.migrate();
		assertEquals(150, jdbc.count("SELECT value FROM \"\"\"v\"\"\""));
		flyway.clean();
		flyway.migrate();
	}

	@Test
	void materializedview() {
		migrateCleanMigrate("materializedview");
	}

	@Test
	void inheritance() {
		migrateCleanMigrate("inheritance");
	}

	@Test
	void domain() {
		Flylite flyway = flyway().locations(PG + "domain").load();
		flyway.migrate();
		assertEquals("foo", jdbc.string("SELECT x FROM t"));
		flyway.clean();
		flyway.migrate();
	}

	@Test
	void enumeration() {
		Flylite flyway = flyway().locations(PG + "enum").load();
		flyway.migrate();
		assertEquals("positive", jdbc.string("SELECT x FROM t"));
		flyway.clean();
		flyway.migrate();
	}

	@Test
	void aggregate() {
		migrateCleanMigrate("aggregate");
	}

	@Test
	void dollarQuote() {
		flyway().locations(PG + "dollar").load().migrate();
		assertEquals(9, jdbc.count("select count(*) from dollar"));
	}

	@Test
	void multiLine() {
		flyway().locations(PG + "multiline").load().migrate();
		assertEquals(1, jdbc.count("select count(*) from address"));
	}

	@Test
	void copy() {
		flyway().locations(PG + "copy").load().migrate();
		assertEquals(6, jdbc.count("select count(*) from copy_test"));
	}

	/**
	 * The lock on the schema history table must not block SQL commands in migrations.
	 * This test won't fail if there's a too restrictive lock - it would just hang.
	 */
	@Test
	void lock() {
		flyway().locations(PG + "lock").load().migrate();
	}

	@Test
	void emptySearchPath() {
		var dataSource = new org.postgresql.ds.PGSimpleDataSource() {
			@Override
			public Connection getConnection() throws SQLException {
				Connection connection = DriverManager.getConnection(database.url(), database.user(),
						database.password());
				try (Statement statement = connection.createStatement()) {
					statement.execute("SELECT set_config('search_path', '', false)");
				}
				return connection;
			}
		};
		Flylite.configure().dataSource(dataSource).locations(BASEDIR).schemas("public").load().migrate();
	}

	@Test
	void warning() {
		// Log should contain "This is a warning"
		assertThrows(FlyliteException.class, () -> flyway().locations(PG + "warning").load().migrate());
	}

	@Test
	void migrateMultipleSchemas() {
		var config = flyway().schemas("flyway_1", "flyway_2", "flyway_3")
			.locations(MIGRATIONDIR + "/multi")
			.placeholders(Map.of("schema1", "\"flyway_1\"", "schema2", "\"flyway_2\"", "schema3", "\"flyway_3\""));
		Flylite flyway = config.load();
		flyway.migrate();
		assertEquals("2.0", version(flyway).toString());
		assertEquals("Add foreign key", flyway.info().current().getDescription());
		assertEquals(0, flyway.migrate().migrationsExecuted);
		// with the schema creation marker
		assertEquals(4, flyway.info().applied().length);
		assertEquals(2, jdbc.count("select count(*) from flyway_1.test_user1"));
		assertEquals(2, jdbc.count("select count(*) from flyway_2.test_user2"));
		assertEquals(2, jdbc.count("select count(*) from flyway_3.test_user3"));
		flyway.clean();
	}

	@Test
	void setCurrentSchema() {
		jdbc.execute("CREATE SCHEMA current_schema_test");
		flyway().schemas("current_schema_test")
			.locations(MIGRATIONDIR + "/current_schema")
			.placeholders(Map.of("schema1", "\"current_schema_test\""))
			.load()
			.migrate();
	}

	@Test
	void schemaExists() {
		assertEquals(1, jdbc.count("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'public'"));
	}

}
