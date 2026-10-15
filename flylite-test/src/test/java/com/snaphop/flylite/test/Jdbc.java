package com.snaphop.flylite.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Tiny JDBC helper for assertions. Opens a connection per call.
 */
public final class Jdbc {

	private final Db.TestDatabase database;

	Jdbc(Db.TestDatabase database) {
		this.database = database;
	}

	public Connection connect() throws SQLException {
		return DriverManager.getConnection(database.url(), database.user(), database.password());
	}

	public void execute(String sql) {
		try (Connection c = connect(); Statement s = c.createStatement()) {
			s.execute(sql);
		}
		catch (SQLException e) {
			throw new IllegalStateException(sql, e);
		}
	}

	public long count(String sql) {
		String v = string(sql);
		return Long.parseLong(v);
	}

	public String string(String sql) {
		List<String> values = strings(sql);
		if (values.size() != 1) {
			throw new IllegalStateException("Expected one row for " + sql + " got " + values);
		}
		return values.get(0);
	}

	public List<String> strings(String sql) {
		try (Connection c = connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
			List<String> values = new ArrayList<>();
			while (rs.next()) {
				values.add(rs.getString(1));
			}
			return values;
		}
		catch (SQLException e) {
			throw new IllegalStateException(sql, e);
		}
	}

	public boolean tableExists(String table) {
		try (Connection c = connect(); ResultSet rs = c.getMetaData().getTables(null, null, table, null)) {
			return rs.next();
		}
		catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}

}
