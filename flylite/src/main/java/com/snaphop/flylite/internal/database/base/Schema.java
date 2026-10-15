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
package com.snaphop.flylite.internal.database.base;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.internal.logging.Log;
import com.snaphop.flylite.internal.exception.FlyliteSqlException;
import com.snaphop.flylite.internal.jdbc.JdbcTemplate;
import com.snaphop.flylite.internal.jdbc.JdbcUtils;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public abstract class Schema<D extends Database<?>, T extends Table<?, ?>> {

	private static final Log LOG = Log.of(Schema.class);

	protected final JdbcTemplate jdbcTemplate;

	protected final D database;

	protected final String name;

	/**
	 * @param jdbcTemplate The Jdbc Template for communicating with the DB.
	 * @param database The database-specific support.
	 * @param name The name of the schema.
	 */
	public Schema(JdbcTemplate jdbcTemplate, D database, String name) {
		this.jdbcTemplate = jdbcTemplate;
		this.database = database;
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public boolean exists() {
		try {
			return doExists();
		}
		catch (SQLException e) {
			throw new FlyliteSqlException("Unable to check whether schema " + this + " exists", e);
		}
	}

	/**
	 * Checks whether this schema exists.
	 * @throws SQLException when the check failed.
	 */
	protected abstract boolean doExists() throws SQLException;

	public boolean empty() {
		try {
			return doEmpty();
		}
		catch (SQLException e) {
			throw new FlyliteSqlException("Unable to check whether schema " + this + " is empty", e);
		}
	}

	/**
	 * Checks whether this schema is empty.
	 * @throws SQLException when the check failed.
	 */
	protected abstract boolean doEmpty() throws SQLException;

	/**
	 * Creates this schema in the database.
	 */
	public void create() {
		try {
			LOG.info("Creating schema " + this + " ...");
			doCreate();
		}
		catch (SQLException e) {
			throw new FlyliteSqlException("Unable to create schema " + this, e);
		}
	}

	/**
	 * Creates this schema in the database.
	 * @throws SQLException when the creation failed.
	 */
	protected abstract void doCreate() throws SQLException;

	/**
	 * Drops this schema from the database.
	 */
	public void drop() {
		try {
			doDrop();
		}
		catch (SQLException e) {
			throw new FlyliteSqlException("Unable to drop schema " + this, e);
		}
	}

	/**
	 * Drops this schema from the database.
	 * @throws SQLException when the drop failed.
	 */
	protected abstract void doDrop() throws SQLException;

	/**
	 * Cleans all the objects in this schema.
	 */
	public void clean() {
		try {
			doClean();
		}
		catch (SQLException e) {
			throw new FlyliteSqlException("Unable to clean schema " + this, e);
		}
	}

	/**
	 * Cleans all the objects in this schema.
	 * @throws SQLException when the clean failed.
	 */
	protected abstract void doClean() throws SQLException;

	/**
	 * Retrieves all the tables in this schema.
	 */
	public T[] allTables() {
		try {
			return doAllTables();
		}
		catch (SQLException e) {
			throw new FlyliteSqlException("Unable to retrieve all tables in schema " + this, e);
		}
	}

	/**
	 * Retrieves all the tables in this schema.
	 * @throws SQLException when the retrieval failed.
	 */
	protected abstract T[] doAllTables() throws SQLException;

	/**
	 * Retrieves the table with this name in this schema.
	 */
	public abstract Table<?, ?> getTable(String tableName);

	/**
	 * Returns the quoted name of this schema.
	 * @return The quoted name of this schema.
	 */
	@Override
	public String toString() {
		return database.quote(name);
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Schema<?, ?> schema)) {
			return false;
		}
		return name.equals(schema.name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

}
