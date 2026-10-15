package db.migration;

import java.sql.Statement;

import com.snaphop.flylite.api.migration.BaseJavaMigration;
import com.snaphop.flylite.api.migration.Context;

public class V2__Java extends BaseJavaMigration {

	@Override
	public void migrate(Context context) throws Exception {
		try (Statement s = context.getConnection().createStatement()) {
			s.execute("INSERT INTO greeting (id, text) VALUES (2, 'from java')");
		}
	}

}
