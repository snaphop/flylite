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
package com.snaphop.flylite.internal.sqlscript;

import java.io.IOException;
import java.io.Reader;
import java.util.Properties;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.ResourceProvider;
import com.snaphop.flylite.api.resource.LoadableResource;
import com.snaphop.flylite.internal.logging.Log;
import com.snaphop.flylite.internal.parser.Parser;
import com.snaphop.flylite.internal.parser.PlaceholderReplacingReader;

/**
 * Per script configuration read from a {@code <script>.conf} properties file next to the
 * script. Supported keys are {@code executeInTransaction}, {@code encoding} and
 * {@code placeholderReplacement}.
 */
public final class SqlScriptMetadata {

	private static final Log LOG = Log.of(SqlScriptMetadata.class);

	private static final String EXECUTE_IN_TRANSACTION = "executeInTransaction";

	private static final String ENCODING = "encoding";

	private static final String PLACEHOLDER_REPLACEMENT = "placeholderReplacement";

	private static final Set<String> KEYS = Set.of(EXECUTE_IN_TRANSACTION, ENCODING, PLACEHOLDER_REPLACEMENT);

	private final @Nullable Boolean executeInTransaction;

	private final @Nullable String encoding;

	private final boolean placeholderReplacement;

	private SqlScriptMetadata(Properties metadata) {
		for (String key : metadata.stringPropertyNames()) {
			if (!KEYS.contains(key)) {
				throw new FlyliteException("Unknown script configuration property: " + key);
			}
		}
		String tx = metadata.getProperty(EXECUTE_IN_TRANSACTION);
		this.executeInTransaction = tx == null ? null : parseBoolean(EXECUTE_IN_TRANSACTION, tx);
		this.encoding = metadata.getProperty(ENCODING);
		String pr = metadata.getProperty(PLACEHOLDER_REPLACEMENT);
		this.placeholderReplacement = pr == null || parseBoolean(PLACEHOLDER_REPLACEMENT, pr);
	}

	private static boolean parseBoolean(String key, String value) {
		String v = value.trim();
		if ("true".equalsIgnoreCase(v)) {
			return true;
		}
		if ("false".equalsIgnoreCase(v)) {
			return false;
		}
		throw new FlyliteException("Invalid value for " + key + " (should be either true or false): " + value);
	}

	public @Nullable Boolean executeInTransaction() {
		return executeInTransaction;
	}

	public @Nullable String encoding() {
		return encoding;
	}

	public boolean placeholderReplacement() {
		return placeholderReplacement;
	}

	public boolean shouldExecute() {
		return true;
	}

	public static SqlScriptMetadata fromResource(@Nullable LoadableResource resource, @Nullable Parser parser) {
		Properties properties = new Properties();
		if (resource != null) {
			LOG.debug("Found script configuration: " + resource.getFilename());
			Reader reader = parser == null ? resource.read()
					: PlaceholderReplacingReader.create(parser.configuration, parser.parsingContext, resource.read());
			try (reader) {
				properties.load(reader);
			}
			catch (IOException e) {
				throw new FlyliteException("Unable to read script configuration: " + resource.getFilename(), e);
			}
		}
		return new SqlScriptMetadata(properties);
	}

	public static @Nullable LoadableResource getMetadataResource(@Nullable ResourceProvider resourceProvider,
			LoadableResource resource) {
		if (resourceProvider == null) {
			return null;
		}
		return resourceProvider.getResource(resource.getRelativePath() + ".conf");
	}

}
