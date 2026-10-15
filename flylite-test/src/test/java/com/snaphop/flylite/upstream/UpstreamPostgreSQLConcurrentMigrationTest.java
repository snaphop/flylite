/*
 * Ported from Flyway 4.2 (flyway-core/src/test/java/org/flywaydb/core/migration/ConcurrentMigrationTestCase.java and PostgreSQLConcurrentMigrationMediumTest.java).
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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.snaphop.flylite.Flylite;
import com.snaphop.flylite.test.Db;

/**
 * Flyway's concurrent migration test for PostgreSQL.
 */
class UpstreamPostgreSQLConcurrentMigrationTest {

	/**
	 * The number of threads to use in this test.
	 */
	private static final int NUM_THREADS = 10;

	@TempDir
	Path dir;

	@Test
	void migrateConcurrently() throws Exception {
		var database = Db.POSTGRES.create(dir);
		var config = database.configure()
			.locations("upstream/migration/concurrent")
			.schemas("concurrent_test")
			.placeholders(Map.of("schema", "\"concurrent_test\""))
			.baselineVersion("0.1");
		AtomicReference<String> error = new AtomicReference<>();
		Thread[] threads = new Thread[NUM_THREADS];
		for (int i = 0; i < NUM_THREADS; i++) {
			threads[i] = new Thread(() -> {
				try {
					config.load().migrate();
				}
				catch (Exception e) {
					error.set(e.getMessage());
				}
			});
		}
		for (Thread thread : threads) {
			thread.start();
		}
		for (Thread thread : threads) {
			thread.join();
		}
		assertNull(error.get(), error.get());
		Flylite flyway = config.load();
		// 4 migrations and the schema creation marker
		assertEquals(5, flyway.info().applied().length);
		assertEquals("2.0", String.valueOf(flyway.info().current().getVersion()));
		assertEquals(0, flyway.migrate().migrationsExecuted);
		assertEquals(2, database.jdbc().count("SELECT COUNT(*) FROM concurrent_test.test_user"));
	}

}
