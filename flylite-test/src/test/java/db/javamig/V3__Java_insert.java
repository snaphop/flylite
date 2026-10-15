package db.javamig;

import java.sql.PreparedStatement;

import com.snaphop.flylite.api.migration.BaseJavaMigration;
import com.snaphop.flylite.api.migration.Context;

/**
 * Found by scanning the {@code db/javamig} location.
 */
public class V3__Java_insert extends BaseJavaMigration {

	@Override
	public void migrate(Context context) throws Exception {
		try (PreparedStatement ps = context.getConnection()
			.prepareStatement("INSERT INTO classpath_test (id, name) VALUES (?, ?)")) {
			ps.setInt(1, 3);
			ps.setString(2, getClass().getSimpleName());
			ps.executeUpdate();
		}
	}

}
