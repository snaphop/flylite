package com.snaphop.flylite.modular;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snaphop.flylite.Flylite;

/**
 * Runs Flylite as the named module {@code com.snaphop.flylite} (surefire patches this
 * test into the module) to make sure the module descriptor is usable. Everything else is
 * tested end to end in flylite-test.
 */
class ModulePathTest {

	@TempDir
	Path dir;

	@Test
	void migratesAsNamedModule() throws Exception {
		assertTrue(Flylite.class.getModule().isNamed());
		assertEquals("com.snaphop.flylite", Flylite.class.getModule().getName());
		Path migrations = Files.createDirectories(dir.resolve("migrations"));
		Files.writeString(migrations.resolve("V1__Init.sql"), "CREATE TABLE t (id INT);\n");
		Flylite flylite = Flylite.configure()
			.dataSource("jdbc:sqlite:" + dir.resolve("module.db"), null, null)
			.locations("filesystem:" + migrations)
			.load();
		assertEquals(1, flylite.migrate().migrationsExecuted);
		flylite.validate();
	}

}
