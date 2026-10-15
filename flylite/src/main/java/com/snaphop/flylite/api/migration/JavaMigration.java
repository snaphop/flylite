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
package com.snaphop.flylite.api.migration;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.api.MigrationType;
import com.snaphop.flylite.api.MigrationVersion;

/**
 * Interface to be implemented by Java-based Migrations.
 *
 * <p>
 * Java-based migrations are a great fit for all changes that can not easily be expressed
 * using SQL.
 * </p>
 *
 * <p>
 * These would typically be things like
 * </p>
 * <ul>
 * <li>BLOB &amp; CLOB changes</li>
 * <li>Advanced bulk data changes (Recalculations, advanced format changes, …)</li>
 * </ul>
 *
 * <p>
 * Migration classes implementing this interface will be automatically discovered when
 * placed in a location on the classpath.
 * </p>
 *
 * <p>
 * Most users will be better served by subclassing subclass {@link BaseJavaMigration}
 * instead of implementing this interface directly, as {@link BaseJavaMigration}
 * encourages the use of Flylite's default naming convention and comes with sensible
 * default implementations of all methods (except migrate of course) while at the same
 * time also providing better isolation against future additions to this interface.
 * </p>
 */
public interface JavaMigration {

	/**
	 * Returns the version of the schema after the migration is complete. {@code null} for
	 * @return The version of the schema after the migration is complete. {@code null} for
	 * repeatable migrations.
	 */
	@Nullable MigrationVersion getVersion();

	/**
	 * Returns the description of this migration for the migration history. Never
	 * @return The description of this migration for the migration history. Never
	 * {@code null}.
	 */
	String getDescription();

	/**
	 * Returns the checksum of this migration.
	 * @return The checksum of this migration.
	 */
	@Nullable Integer getChecksum();

	/**
	 * Whether the execution should take place inside a transaction. Almost all
	 * implementations should return {@code true}. This however makes it possible to
	 * execute certain migrations outside a transaction. This is useful for databases like
	 * PostgreSQL and SQL Server where certain statement can only execute outside a
	 * transaction.
	 * @return {@code true} if a transaction should be used (highly recommended), or
	 * {@code false} if not.
	 */
	boolean canExecuteInTransaction();

	/**
	 * Executes this migration. The execution will automatically take place within a
	 * transaction, when the underlying database supports it and the
	 * canExecuteInTransaction returns {@code true}.
	 * @param context The context relevant for this migration, containing things like the
	 * JDBC connection to use and the current Flylite configuration.
	 * @throws Exception when the migration failed.
	 */
	void migrate(Context context) throws Exception;

	default MigrationType getType() {
		return MigrationType.JDBC;
	}

}