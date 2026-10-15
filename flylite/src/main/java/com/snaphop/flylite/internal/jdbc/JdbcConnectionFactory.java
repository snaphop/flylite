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

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.internal.logging.Log;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.internal.database.DatabaseType;
import com.snaphop.flylite.internal.database.DatabaseTypeRegister;
import com.snaphop.flylite.internal.exception.FlyliteSqlException;
import com.snaphop.flylite.internal.util.ExceptionUtils;
import javax.sql.DataSource;
import java.io.Closeable;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * Utility class for dealing with jdbc connections.
 */
public class JdbcConnectionFactory implements Closeable {

	private static final Log LOG = Log.of(JdbcConnectionFactory.class);

	private final DataSource dataSource;

	private final int connectRetries;

	private final int connectRetriesInterval;

	private final DatabaseType databaseType;

	private final String jdbcUrl;

	private final String driverInfo;

	private final String productName;

	private @Nullable Connection firstConnection;

	private @Nullable ConnectionInitializer connectionInitializer;

	/**
	 * Creates a new JDBC connection factory. This automatically opens a first connection
	 * which can be obtained via a call to getConnection and which must be closed again to
	 * avoid leaking it.
	 * @param dataSource The DataSource to obtain the connection from.
	 * @param configuration The Flylite configuration.
	 */
	public JdbcConnectionFactory(DataSource dataSource, Configuration configuration) {
		this.dataSource = dataSource;
		this.connectRetries = configuration.getConnectRetries();
		this.connectRetriesInterval = configuration.getConnectRetriesInterval();
		Connection first = JdbcUtils.openConnection(dataSource, connectRetries, connectRetriesInterval);
		this.firstConnection = first;
		this.databaseType = DatabaseTypeRegister.getDatabaseTypeForConnection(first);
		final DatabaseMetaData databaseMetaData = JdbcUtils.getDatabaseMetaData(first);
		this.jdbcUrl = getJdbcUrl(databaseMetaData);
		this.driverInfo = getDriverInfo(databaseMetaData);
		this.productName = JdbcUtils.getDatabaseProductName(databaseMetaData);
	}

	public void setConnectionInitializer(ConnectionInitializer connectionInitializer) {
		this.connectionInitializer = connectionInitializer;
	}

	public Connection openConnection() throws FlyliteException {
		Connection first = firstConnection;
		Connection connection = first == null
				? JdbcUtils.openConnection(dataSource, connectRetries, connectRetriesInterval) : first;
		firstConnection = null;
		ConnectionInitializer initializer = connectionInitializer;
		if (initializer != null) {
			initializer.initialize(this, connection);
		}
		return connection;
	}

	@Override
	public void close() {
		Connection first = firstConnection;
		if (first != null) {
			try {
				first.close();
			}
			catch (Exception e) {
				LOG.error("Error while closing connection: " + e.getMessage(), e);
			}
			firstConnection = null;
		}
	}

	public interface ConnectionInitializer {

		void initialize(JdbcConnectionFactory jdbcConnectionFactory, Connection connection);

	}

	private static String getJdbcUrl(DatabaseMetaData databaseMetaData) {
		String url;
		try {
			url = databaseMetaData.getURL();
		}
		catch (SQLException e) {
			throw new FlyliteSqlException("Unable to retrieve the JDBC connection URL!", e);
		}
		if (url == null) {
			return "";
		}
		return filterUrl(url);
	}

	/**
	 * Filter out URL parameters to avoid including passwords etc.
	 */
	static String filterUrl(String url) {
		int questionMark = url.indexOf("?");
		if (questionMark >= 0 && !url.contains("?databaseName=")) {
			url = url.substring(0, questionMark);
		}
		url = url.replaceAll("://.*:.*@", "://");
		return url;
	}

	private static String getDriverInfo(DatabaseMetaData databaseMetaData) {
		try {
			return databaseMetaData.getDriverName() + " " + databaseMetaData.getDriverVersion();
		}
		catch (SQLException e) {
			throw new FlyliteSqlException("Unable to read database driver info: " + e.getMessage(), e);
		}
	}

	public DatabaseType getDatabaseType() {
		return this.databaseType;
	}

	public String getJdbcUrl() {
		return this.jdbcUrl;
	}

	public String getDriverInfo() {
		return this.driverInfo;
	}

	public String getProductName() {
		return this.productName;
	}

}
