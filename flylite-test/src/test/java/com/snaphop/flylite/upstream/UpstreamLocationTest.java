/*
 * Ported from Flyway 4.2 (flyway-core/src/test/java/org/flywaydb/core/internal/util/LocationSmallTest.java).
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

import org.junit.jupiter.api.Test;

import com.snaphop.flylite.api.Location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for location.
 */
class UpstreamLocationTest {

	@Test
	public void defaultPrefix() {
		Location location = new Location("db/migration");
		assertEquals("classpath:", location.getPrefix());
		assertTrue(location.isClassPath());
		assertEquals("db/migration", location.getPath());
		assertEquals("classpath:db/migration", location.getDescriptor());
	}

	@Test
	public void classpathPrefix() {
		Location location = new Location("classpath:db/migration");
		assertEquals("classpath:", location.getPrefix());
		assertTrue(location.isClassPath());
		assertEquals("db/migration", location.getPath());
		assertEquals("classpath:db/migration", location.getDescriptor());
	}

	@Test
	public void filesystemPrefix() {
		Location location = new Location("filesystem:db/migration");
		assertEquals("filesystem:", location.getPrefix());
		assertFalse(location.isClassPath());
		assertEquals("db/migration", location.getPath());
		assertEquals("filesystem:db/migration", location.getDescriptor());
	}

	@Test
	public void filesystemPrefixAbsolutePath() {
		Location location = new Location("filesystem:/db/migration");
		assertEquals("filesystem:", location.getPrefix());
		assertFalse(location.isClassPath());
		assertEquals("/db/migration", location.getPath());
		assertEquals("filesystem:/db/migration", location.getDescriptor());
	}

	@Test
	public void filesystemPrefixWithDotsInPath() {
		Location location = new Location("filesystem:util-2.0.4/db/migration");
		assertEquals("filesystem:", location.getPrefix());
		assertFalse(location.isClassPath());
		assertEquals("util-2.0.4/db/migration", location.getPath());
		assertEquals("filesystem:util-2.0.4/db/migration", location.getDescriptor());
	}

}