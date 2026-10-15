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
package com.snaphop.flylite.internal.resolver.java;

import java.sql.Connection;
import java.sql.SQLException;

import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.api.executor.Context;
import com.snaphop.flylite.api.executor.MigrationExecutor;
import com.snaphop.flylite.api.migration.JavaMigration;

/**
 * Adapter for executing migrations implementing JavaMigration.
 */
public final class JavaMigrationExecutor implements MigrationExecutor {

	private final JavaMigration javaMigration;

	public JavaMigrationExecutor(JavaMigration javaMigration) {
		this.javaMigration = javaMigration;
	}

	@Override
	public void execute(final Context context) throws SQLException {
		try {
			javaMigration.migrate(new com.snaphop.flylite.api.migration.Context() {
				@Override
				public Configuration getConfiguration() {
					return context.getConfiguration();
				}

				@Override
				public Connection getConnection() {
					return context.getConnection();
				}
			});
		}
		catch (SQLException | RuntimeException e) {
			throw e;
		}
		catch (Exception e) {
			throw new FlyliteException("Migration failed !", e);
		}
	}

	@Override
	public boolean canExecuteInTransaction() {
		return javaMigration.canExecuteInTransaction();
	}

	@Override
	public boolean shouldExecute() {
		return true;
	}

}
