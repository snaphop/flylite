/*
 * Copyright (C) Red Gate Software Ltd 2010-2023
 * Modifications Copyright (C) Snaphop 2026
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
package com.snaphop.flylite.internal.jdbc;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.api.ErrorCode;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.internal.database.DatabaseType;
import com.snaphop.flylite.internal.database.DatabaseTypeRegister;
import com.snaphop.flylite.internal.util.ClassUtils;

/**
 * YAGNI: The simplest DataSource implementation that works for Flylite. Used when a JDBC
 * url is configured instead of a {@link DataSource}.
 */
public final class DriverDataSource implements DataSource {

	private final Driver driver;

	private final String url;

	private final @Nullable String user;

	private final @Nullable String password;

	private final Properties properties;

	/**
	 * Creates a new DriverDataSource.
	 * @param classLoader the class loader used to load the driver.
	 * @param driverClass the name of the JDBC Driver class to use. {@code null} for
	 * url-based autodetection.
	 * @param url the JDBC URL.
	 * @param user the user or {@code null}.
	 * @param password the password or {@code null}.
	 * @param jdbcProperties extra driver properties which override the database defaults.
	 * @throws FlyliteException when the datasource could not be created.
	 */
	public DriverDataSource(ClassLoader classLoader, @Nullable String driverClass, String url, @Nullable String user,
			@Nullable String password, Map<String, String> jdbcProperties) throws FlyliteException {
		this.url = url;
		DatabaseType type = DatabaseTypeRegister.getDatabaseTypeForUrl(url);
		String resolvedDriverClass = driverClass == null || driverClass.isBlank() ? type.getDriverClass(url)
				: driverClass;
		Properties props = new Properties();
		type.setDefaultConnectionProps(props);
		props.putAll(jdbcProperties);
		this.properties = props;
		try {
			this.driver = ClassUtils.instantiate(resolvedDriverClass, classLoader, Driver.class);
		}
		catch (FlyliteException e) {
			throw new FlyliteException("Unable to instantiate JDBC driver: " + resolvedDriverClass
					+ " => Check whether the jar file is present", e, ErrorCode.JDBC_DRIVER);
		}
		this.user = user;
		this.password = password;
	}

	@Override
	public Connection getConnection() throws SQLException {
		return getConnection(user, password);
	}

	@Override
	public Connection getConnection(@Nullable String username, @Nullable String password) throws SQLException {
		Properties props = new Properties();
		props.putAll(this.properties);
		if (username != null) {
			props.setProperty("user", username);
		}
		if (password != null) {
			props.setProperty("password", password);
		}
		Connection connection = driver.connect(url, props);
		if (connection == null) {
			throw new FlyliteException("Unable to connect to " + DatabaseTypeRegister.redactJdbcUrl(url));
		}
		connection.setAutoCommit(true);
		return connection;
	}

	@Override
	public int getLoginTimeout() {
		return 0;
	}

	@Override
	public void setLoginTimeout(int timeout) {
		throw new UnsupportedOperationException("setLoginTimeout");
	}

	@Override
	public PrintWriter getLogWriter() {
		throw new UnsupportedOperationException("getLogWriter");
	}

	@Override
	public void setLogWriter(PrintWriter pw) {
		throw new UnsupportedOperationException("setLogWriter");
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		if (iface.isInstance(this)) {
			return iface.cast(this);
		}
		throw new SQLException("Not a wrapper for " + iface);
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) {
		return iface.isInstance(this);
	}

	@Override
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		throw new SQLFeatureNotSupportedException("getParentLogger");
	}

	/**
	 * The JDBC url.
	 * @return url.
	 */
	public String getUrl() {
		return url;
	}

}
