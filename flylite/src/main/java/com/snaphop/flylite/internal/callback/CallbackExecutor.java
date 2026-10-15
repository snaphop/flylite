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
package com.snaphop.flylite.internal.callback;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.api.MigrationInfo;
import com.snaphop.flylite.api.callback.Event;
import com.snaphop.flylite.api.output.OperationResult;

/**
 * Executes the callbacks for a specific event.
 */
public interface CallbackExecutor {

	/**
	 * Executes the callbacks for this event on the main connection.
	 * @param event the event.
	 */
	void onEvent(Event event);

	/**
	 * Executes the callbacks for a migrate event on a separate event connection.
	 * @param event the event.
	 */
	void onMigrateEvent(Event event);

	/**
	 * Sets the migration currently being migrated.
	 * @param migrationInfo the migration or {@code null}.
	 */
	void setMigrationInfo(@Nullable MigrationInfo migrationInfo);

	/**
	 * Executes the callbacks for an "each" event within a migrate operation on the
	 * migration connection.
	 * @param event the event.
	 */
	void onEachMigrateEvent(Event event);

	/**
	 * Executes the callbacks for an operation finish event.
	 * @param event the event.
	 * @param operationResult the result.
	 */
	void onOperationFinishEvent(Event event, OperationResult operationResult);

}
