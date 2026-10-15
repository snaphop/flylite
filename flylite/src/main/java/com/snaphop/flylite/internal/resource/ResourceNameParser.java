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
package com.snaphop.flylite.internal.resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.api.MigrationVersion;
import com.snaphop.flylite.api.callback.Event;
import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.internal.util.StringUtils;

public class ResourceNameParser {

	private enum ResourceType {

		MIGRATION, REPEATABLE_MIGRATION, CALLBACK;

		boolean isVersioned() {
			return this == MIGRATION;
		}

	}

	private record Prefix(String prefix, ResourceType type) {
	}

	private record Split(String left, String right) {
	}

	private final Configuration configuration;

	private final List<Prefix> prefixes;

	public ResourceNameParser(Configuration configuration) {
		this.configuration = configuration;
		// Versioned migrations are named in the form
		// prefixVERSIONseparatorDESCRIPTIONsuffix
		// Repeatable migrations and callbacks are named in the form
		// prefixSeparatorDESCRIPTIONsuffix
		prefixes = populatePrefixes(configuration);
	}

	public ResourceName parse(String resourceName) {
		return parse(resourceName, configuration.getSqlMigrationSuffixes());
	}

	public ResourceName parse(String resourceName, Collection<String> suffixes) {
		// Strip off suffixes
		Split suffixResult = stripSuffix(resourceName, suffixes);

		// Find the appropriate prefix
		Prefix prefix = findPrefix(suffixResult.left(), prefixes);
		if (prefix != null) {
			// Strip off prefix
			String name = suffixResult.left().substring(prefix.prefix().length());
			String separator = configuration.getSqlMigrationSeparator();
			var splitName = StringUtils.splitAtFirstSeparator(name, separator);
			String version = splitName.getLeft();
			String rawDescription = splitName.getRight();
			boolean isValid = true;
			String validationMessage = "";
			String exampleDescription = rawDescription.isEmpty() ? "description" : rawDescription;

			// Validate the name
			if (!prefix.type().isVersioned()) {
				// Must not have a version (that is, something before the separator)
				if (!version.isEmpty()) {
					isValid = false;
					validationMessage = "Invalid repeatable migration / callback name format: " + resourceName
							+ " (It cannot contain a version and should look like this: " + prefix.prefix() + separator
							+ exampleDescription + suffixResult.right() + ")";
				}
			}
			else {
				// Must have a version (that is, something before the separator)
				if (version.isEmpty()) {
					isValid = false;
					validationMessage = "Invalid versioned migration name format: " + resourceName
							+ " (It must contain a version and should look like this: " + prefix.prefix() + "1.2"
							+ separator + exampleDescription + suffixResult.right() + ")";
				}
				else {
					// ... and that must be a legitimate version
					try {
						MigrationVersion.fromVersion(version);
					}
					catch (Exception e) {
						isValid = false;
						validationMessage = "Invalid versioned migration name format: " + resourceName
								+ " (could not recognise version number " + version + ")";
					}
				}
			}

			String description = rawDescription.replace("_", " ");
			return new ResourceName(prefix.prefix(), version, separator, description, rawDescription,
					suffixResult.right(), isValid, validationMessage);
		}

		// Didn't match any prefix
		return ResourceName.invalid("Unrecognised migration name format: " + resourceName);
	}

	private static @Nullable Prefix findPrefix(String nameWithoutSuffix, List<Prefix> prefixes) {
		for (Prefix prefix : prefixes) {
			if (nameWithoutSuffix.startsWith(prefix.prefix())) {
				return prefix;
			}
		}
		return null;
	}

	private static Split stripSuffix(String name, Collection<String> suffixes) {
		for (String suffix : suffixes) {
			if (name.toUpperCase(Locale.ROOT).endsWith(suffix.toUpperCase(Locale.ROOT))) {
				return new Split(name.substring(0, name.length() - suffix.length()),
						name.substring(name.length() - suffix.length()));
			}
		}
		return new Split(name, "");
	}

	private static List<Prefix> populatePrefixes(Configuration configuration) {
		List<Prefix> prefixes = new ArrayList<>();
		prefixes.add(new Prefix(configuration.getSqlMigrationPrefix(), ResourceType.MIGRATION));
		prefixes.add(new Prefix(configuration.getRepeatableSqlMigrationPrefix(), ResourceType.REPEATABLE_MIGRATION));
		for (Event event : Event.values()) {
			prefixes.add(new Prefix(event.getId(), ResourceType.CALLBACK));
		}
		// Sort most-hard-to-match first; that is, in descending order of prefix length
		prefixes.sort(Comparator.comparingInt((Prefix p) -> p.prefix().length()).reversed());
		return prefixes;
	}

}
