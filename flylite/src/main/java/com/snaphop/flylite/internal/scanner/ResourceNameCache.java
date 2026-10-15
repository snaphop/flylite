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
package com.snaphop.flylite.internal.scanner;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.internal.scanner.classpath.ClassPathLocationScanner;

/**
 * Caches the resource names found by each location scanner for each url so that repeated
 * commands do not scan the classpath again.
 */
public class ResourceNameCache {

	/**
	 * Resource names by scanner and url. Urls are keyed by their external form because
	 * {@link URL#equals(Object)} resolves host names.
	 */
	private final Map<ClassPathLocationScanner, Map<String, Set<String>>> resourceNameCache = new HashMap<>();

	public void put(ClassPathLocationScanner classPathLocationScanner, URL resolvedUrl, Set<String> names) {
		resourceNameCache.computeIfAbsent(classPathLocationScanner, k -> new HashMap<>())
			.put(resolvedUrl.toExternalForm(), names);
	}

	public @Nullable Set<String> get(ClassPathLocationScanner classPathLocationScanner, URL resolvedUrl) {
		Map<String, Set<String>> names = resourceNameCache.get(classPathLocationScanner);
		return names == null ? null : names.get(resolvedUrl.toExternalForm());
	}

}
