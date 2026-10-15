package com.snaphop.flylite.test;

import java.sql.Statement;

import com.snaphop.flylite.api.MigrationVersion;
import com.snaphop.flylite.api.migration.Context;
import com.snaphop.flylite.api.migration.JavaMigration;

/**
 * A java migration instance passed to the configuration instead of being scanned.
 */
class FixedJavaMigration implements JavaMigration {

	@Override
	public MigrationVersion getVersion() {
		return MigrationVersion.fromVersion("5");
	}

	@Override
	public String getDescription() {
		return "Fixed";
	}

	@Override
	public Integer getChecksum() {
		return null;
	}

	@Override
	public boolean canExecuteInTransaction() {
		return true;
	}

	@Override
	public void migrate(Context context) throws Exception {
		try (Statement s = context.getConnection().createStatement()) {
			s.executeUpdate("INSERT INTO classpath_test (id, name) VALUES (5, 'fixed 5')");
		}
	}

}
