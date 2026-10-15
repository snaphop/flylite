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
package com.snaphop.flylite.api.output;

/**
 * Info about a single migration.
 *
 * @param category Versioned, Repeatable, Baseline or empty for synthetic migrations.
 * @param version the version or empty.
 * @param rawVersion the version as written in the migration name or empty.
 * @param description the description.
 * @param type the migration type.
 * @param installedOnUTC the instant the migration was installed or empty.
 * @param state the state display name.
 * @param filepath the physical location or empty.
 * @param installedBy who installed the migration or empty.
 * @param executionTime execution time in milliseconds.
 */
public record InfoOutput(String category, String version, String rawVersion, String description, String type,
		String installedOnUTC, String state, String filepath, String installedBy, int executionTime) {
}
