package com.snaphop.flylite.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.snaphop.flylite.Flylite;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.MigrationInfo;

/**
 * End to end tests of where migrations are found: jars on the classpath (how libraries
 * ship migrations), wildcards and multiple locations.
 */
class LocationTest {

	@TempDir
	Path dir;

	private Path jar(String name, String... entries) throws IOException {
		Path jar = dir.resolve(name);
		try (OutputStream out = Files.newOutputStream(jar); JarOutputStream jos = new JarOutputStream(out)) {
			for (int i = 0; i < entries.length; i += 2) {
				jos.putNextEntry(new JarEntry(entries[i]));
				jos.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
				jos.closeEntry();
			}
		}
		return jar;
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void migrationsInJars(Db db) throws Exception {
		var database = db.create(dir);
		Path library = jar("library.jar", //
				"db/library/migration/", "", //
				"db/library/migration/V1__Library.sql", "CREATE TABLE library (id INT);\n", //
				"db/library/migration/sub/V1_1__Library_sub.sql", "INSERT INTO library VALUES (1);\n", //
				"db/library/migration/not-a-migration.txt", "ignored");
		Path app = jar("app.jar", //
				"db/app/migration/V2__App.sql", "CREATE TABLE app (id INT);\n", //
				"db/app/migration/R__App_view.sql",
				"DROP VIEW IF EXISTS app_view; CREATE VIEW app_view AS SELECT * FROM library;\n");
		try (URLClassLoader loader = new URLClassLoader(new URL[] { library.toUri().toURL(), app.toUri().toURL() },
				getClass().getClassLoader())) {
			Flylite flylite = Flylite.configure(loader)
				.dataSource(database.url(), database.user(), database.password())
				.locations("classpath:db/library/migration", "classpath:db/app/migration")
				.load();
			assertEquals(4, flylite.migrate().migrationsExecuted);
			var scripts = Arrays.stream(flylite.info().all()).map(MigrationInfo::getScript).toList();
			assertEquals(List.of("V1__Library.sql", "sub/V1_1__Library_sub.sql", "V2__App.sql", "R__App_view.sql"),
					scripts);
			assertEquals(library.toAbsolutePath() + "!/db/library/migration/V1__Library.sql",
					flylite.info().all()[0].getPhysicalLocation());
			flylite.validate();
			assertEquals(1, database.jdbc().count("SELECT COUNT(*) FROM app_view"));
		}
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void wildcardLocations(Db db) {
		var database = db.create(dir);
		new Migrations(dir.resolve("modules/a/migration")).add("V1__A.sql", "CREATE TABLE a (id INT);\n");
		new Migrations(dir.resolve("modules/b/migration")).add("V2__B.sql", "CREATE TABLE b (id INT);\n");
		new Migrations(dir.resolve("modules/b/other")).add("V3__Other.sql", "CREATE TABLE other (id INT);\n");
		new Migrations(dir.resolve("modules/c/deep/migration")).add("V4__Deep.sql", "CREATE TABLE deep (id INT);\n");

		var single = database.configure().locations("filesystem:" + dir + "/modules/*/migration").load();
		assertEquals(2, single.migrate().migrationsExecuted);

		var deep = db.create(dir).configure().locations("filesystem:" + dir + "/modules/**/migration").load();
		assertEquals(3, deep.migrate().migrationsExecuted);

		var classpath = db.create(dir).configure().locations("classpath:db/class?ath").load();
		assertEquals(2, classpath.migrate().migrationsExecuted);
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void executeInTransactionFalse(Db db) {
		var database = db.create(dir);
		var migrations = new Migrations(dir.resolve("m")).add("V1__One.sql", "CREATE TABLE one (id INT);\n")
			.add("V2__Fails.sql", "CREATE TABLE two (id INT);\nTHIS IS NOT VALID SQL;\n");
		var config = database.configure()
			.locations(migrations.location())
			.executeInTransaction(false)
			.postgresqlTransactionalLock(false);
		assertThrows(FlyliteException.class, () -> config.load().migrate());
		// without a transaction the first statement of the failed migration stays
		assertTrue(database.jdbc().tableExists("two"));
		assertTrue(config.load().info().current().getState().isFailed());
	}

	@ParameterizedTest
	@EnumSource(Db.class)
	void invalidLocations(Db db) {
		var e = assertThrows(FlyliteException.class, () -> Flylite.configure().locations("s3:bucket/migrations"));
		assertTrue(e.getMessage().contains("Unknown prefix for location"), e.getMessage());
		var database = db.create(dir);
		assertEquals(0, database.configure().locations("classpath:nothing/here").load().migrate().migrationsExecuted);
	}

}
