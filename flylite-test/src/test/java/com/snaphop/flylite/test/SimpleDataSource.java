package com.snaphop.flylite.test;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

/**
 * A plain DataSource like an application would pass to Flylite.
 */
class SimpleDataSource implements DataSource {

	private final Db.TestDatabase database;

	SimpleDataSource(Db.TestDatabase database) {
		this.database = database;
	}

	@Override
	public Connection getConnection() throws SQLException {
		return DriverManager.getConnection(database.url(), database.user(), database.password());
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		return DriverManager.getConnection(database.url(), username, password);
	}

	@Override
	public PrintWriter getLogWriter() {
		return new PrintWriter(System.out);
	}

	@Override
	public void setLogWriter(PrintWriter out) {
	}

	@Override
	public void setLoginTimeout(int seconds) {
	}

	@Override
	public int getLoginTimeout() {
		return 0;
	}

	@Override
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		throw new SQLException("not a wrapper");
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) {
		return false;
	}

}
