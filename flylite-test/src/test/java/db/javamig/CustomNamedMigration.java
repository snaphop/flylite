package db.javamig;

import com.snaphop.flylite.api.MigrationVersion;
import com.snaphop.flylite.api.migration.Context;
import com.snaphop.flylite.api.migration.JavaMigration;

/**
 * Does not follow the naming convention so implements {@link JavaMigration} directly.
 */
public class CustomNamedMigration implements JavaMigration {

	@Override
	public MigrationVersion getVersion() {
		return MigrationVersion.fromVersion("4");
	}

	@Override
	public String getDescription() {
		return "Custom named";
	}

	@Override
	public Integer getChecksum() {
		return 42;
	}

	@Override
	public boolean canExecuteInTransaction() {
		return true;
	}

	@Override
	public void migrate(Context context) {
		// nothing to do
	}

}
