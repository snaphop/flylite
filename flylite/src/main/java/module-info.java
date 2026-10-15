/*
 * Copyright (C) Snaphop 2026
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
/**
 * Flylite: database migrations for PostgreSQL and SQLite.
 * <p>
 * Start with {@link com.snaphop.flylite.Flylite#configure()}. Flylite logs with
 * {@link System.Logger} and has no required dependencies besides a JDBC driver.
 * <p>
 * {@code org.postgresql.jdbc} is only needed at runtime for {@code COPY ... FROM STDIN}
 * statements in PostgreSQL migrations.
 */
module com.snaphop.flylite {
	exports com.snaphop.flylite;
	exports com.snaphop.flylite.api;
	exports com.snaphop.flylite.api.callback;
	exports com.snaphop.flylite.api.configuration;
	exports com.snaphop.flylite.api.exception;
	exports com.snaphop.flylite.api.executor;
	exports com.snaphop.flylite.api.migration;
	exports com.snaphop.flylite.api.output;
	exports com.snaphop.flylite.api.pattern;
	exports com.snaphop.flylite.api.resolver;
	exports com.snaphop.flylite.api.resource;

	requires transitive java.sql;
	requires static org.jspecify;
	requires static org.postgresql.jdbc;
}
