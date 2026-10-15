/*
 * Ported from Flyway 4.2 (flyway-core/src/test/java/org/flywaydb/core/api/MigrationVersionSmallTest.java).
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

import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.MigrationVersion;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.snaphop.flylite.api.MigrationVersion.EMPTY;
import static com.snaphop.flylite.api.MigrationVersion.LATEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests MigrationVersion.
 */
class UpstreamMigrationVersionTest {

	@Test
	public void compareTo() {
		MigrationVersion v1 = MigrationVersion.fromVersion("1");
		MigrationVersion v10 = MigrationVersion.fromVersion("1.0");
		MigrationVersion v11 = MigrationVersion.fromVersion("1.1");
		MigrationVersion v1100 = MigrationVersion.fromVersion("1.1.0.0");
		MigrationVersion v1101 = MigrationVersion.fromVersion("1.1.0.1");
		MigrationVersion v2 = MigrationVersion.fromVersion("2");
		MigrationVersion v201004171859 = MigrationVersion.fromVersion("201004171859");
		MigrationVersion v201004180000 = MigrationVersion.fromVersion("201004180000");

		assertTrue(v1.compareTo(v10) == 0);
		assertTrue(v10.compareTo(v1) == 0);
		assertTrue(v1.compareTo(v11) < 0);
		assertTrue(v11.compareTo(v1) > 0);
		assertTrue(v11.compareTo(v1100) == 0);
		assertTrue(v1100.compareTo(v11) == 0);
		assertTrue(v11.compareTo(v1101) < 0);
		assertTrue(v1101.compareTo(v11) > 0);
		assertTrue(v1101.compareTo(v2) < 0);
		assertTrue(v2.compareTo(v1101) > 0);
		assertTrue(v201004171859.compareTo(v201004180000) < 0);
		assertTrue(v201004180000.compareTo(v201004171859) > 0);

		assertTrue(v2.compareTo(LATEST) < 0);
		assertTrue(LATEST.compareTo(v2) > 0);
		assertTrue(v201004180000.compareTo(LATEST) < 0);
		assertTrue(LATEST.compareTo(v201004180000) > 0);
	}

	@Test
	public void testEquals() {
		final MigrationVersion v1 = MigrationVersion.fromVersion("1.2.3.3");
		final MigrationVersion v2 = MigrationVersion.fromVersion("1.2.3.3");
		assertTrue(v1.compareTo(v2) == 0);
		assertEquals(v1.hashCode(), v2.hashCode());
	}

	@Test
	public void testNumber() {
		final MigrationVersion a1 = MigrationVersion.fromVersion("1.2.13.3");
		final MigrationVersion a2 = MigrationVersion.fromVersion("1.2.3.3");
		assertTrue(a1.compareTo(a2) > 0);
	}

	@Test
	public void testLength1() {
		final MigrationVersion a1 = MigrationVersion.fromVersion("1.2.1.3");
		final MigrationVersion a2 = MigrationVersion.fromVersion("1.2.1");
		assertTrue(a1.compareTo(a2) > 0);
	}

	@Test
	public void testLength2() {
		final MigrationVersion a1 = MigrationVersion.fromVersion("1.2.1");
		final MigrationVersion a2 = MigrationVersion.fromVersion("1.2.1.1");
		assertTrue(a1.compareTo(a2) < 0);
	}

	@Test
	public void leadingZeroes() {
		final MigrationVersion v1 = MigrationVersion.fromVersion("1.0");
		final MigrationVersion v001 = MigrationVersion.fromVersion("001.0");
		assertTrue(v1.compareTo(v001) == 0);
		assertTrue(v1.equals(v001));
		assertEquals(v1.hashCode(), v001.hashCode());
	}

	@Test
	public void trailingZeroes() {
		final MigrationVersion v100 = MigrationVersion.fromVersion("1.00");
		final MigrationVersion v1 = MigrationVersion.fromVersion("1");
		assertTrue(v100.compareTo(v1) == 0);
		assertTrue(v100.equals(v1));
		assertEquals(v100.hashCode(), v1.hashCode());
	}

	@Test
	public void fromVersionFactory() {
		assertEquals(LATEST, MigrationVersion.fromVersion(LATEST.getVersion()));
		assertEquals(EMPTY, MigrationVersion.fromVersion(EMPTY.getVersion()));
		assertEquals("1.2.3", MigrationVersion.fromVersion("1.2.3").getVersion());
	}

	@Test
	public void empty() {
		assertEquals(EMPTY, EMPTY);
		assertTrue(EMPTY.compareTo(EMPTY) == 0);
	}

	@Test
	public void latest() {
		assertEquals(LATEST, LATEST);
		assertTrue(LATEST.compareTo(LATEST) == 0);
	}

	@Test
	public void zeros() {
		final MigrationVersion v00 = MigrationVersion.fromVersion("0.0");
		final MigrationVersion v0 = MigrationVersion.fromVersion("0");
		assertTrue(v00.compareTo(v0) == 0);
		assertTrue(v00.equals(v0));
		assertEquals(v00.hashCode(), v0.hashCode());
	}

	@Test
	public void doubleDot() {
		assertThrows(FlyliteException.class, () -> {
			MigrationVersion.fromVersion("1..1");
		});
	}

	@Test
	public void dot() {
		assertThrows(FlyliteException.class, () -> {
			MigrationVersion.fromVersion(".");
		});
	}

	@Test
	public void startDot() {
		assertThrows(FlyliteException.class, () -> {
			MigrationVersion.fromVersion(".1");
		});
	}

	@Test
	public void endDot() {
		assertThrows(FlyliteException.class, () -> {
			MigrationVersion.fromVersion("1.");
		});
	}

	@Test
	public void letters() {
		assertThrows(FlyliteException.class, () -> {
			MigrationVersion.fromVersion("abc1.0");
		});
	}

	@Test
	public void dash() {
		assertThrows(FlyliteException.class, () -> {
			MigrationVersion.fromVersion("1.2.1-3");
		});
	}

	@Test
	public void alphaNumeric() {
		assertThrows(FlyliteException.class, () -> {
			MigrationVersion.fromVersion("1.2.1a-3");
		});
	}

	@Test
	public void testWouldOverflowLong() {
		final String raw = "9999999999999999999999999999999999.8888888231231231231231298797298789132.22";
		MigrationVersion longVersions = MigrationVersion.fromVersion(raw);
		assertEquals(raw, longVersions.getVersion());
	}

}
