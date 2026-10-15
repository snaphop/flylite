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

import com.snaphop.flylite.internal.logging.Log;
import com.snaphop.flylite.api.ClassProvider;
import com.snaphop.flylite.api.Location;
import com.snaphop.flylite.api.ResourceProvider;
import com.snaphop.flylite.api.resource.LoadableResource;
import com.snaphop.flylite.internal.scanner.classpath.ClassPathScanner;
import com.snaphop.flylite.internal.scanner.classpath.ResourceAndClassScanner;
import com.snaphop.flylite.internal.scanner.filesystem.FileSystemScanner;
import com.snaphop.flylite.internal.util.StringUtils;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Scanner for Resources and Classes.
 */
public class Scanner<I> implements ResourceProvider, ClassProvider<I> {

	private static final Log LOG = Log.of(Scanner.class);

	private final List<LoadableResource> resources = new ArrayList<>();

	private final List<Class<? extends I>> classes = new ArrayList<>();

	// Lookup maps to speed up getResource
	private final HashMap<String, LoadableResource> relativeResourceMap = new HashMap<>();

	private HashMap<String, LoadableResource> absoluteResourceMap = null;

	/*
	 * Constructor. Scans the given locations for resources, and classes implementing the
	 * specified interface.
	 */
	public Scanner(Class<I> implementedInterface, Collection<Location> locations, ClassLoader classLoader,
			Charset encoding, ResourceNameCache resourceNameCache, LocationScannerCache locationScannerCache,
			boolean throwOnMissingLocations) {
		FileSystemScanner fileSystemScanner = new FileSystemScanner(encoding, throwOnMissingLocations);
		for (Location location : locations) {
			if (location.isFileSystem()) {
				resources.addAll(fileSystemScanner.scanForResources(location));
			}
			else {
				ResourceAndClassScanner<I> resourceAndClassScanner = new ClassPathScanner<>(implementedInterface,
						classLoader, encoding, location, resourceNameCache, locationScannerCache,
						throwOnMissingLocations);
				resources.addAll(resourceAndClassScanner.scanForResources());
				classes.addAll(resourceAndClassScanner.scanForClasses());
			}
		}
		for (LoadableResource resource : resources) {
			relativeResourceMap.put(resource.getRelativePath().toLowerCase(Locale.ROOT), resource);
		}
	}

	@Override
	public LoadableResource getResource(String name) {
		LoadableResource loadedResource = relativeResourceMap.get(name.toLowerCase(Locale.ROOT));
		if (loadedResource != null) {
			return loadedResource;
		}
		// Only build the HashMap and resolve the absolute paths if an
		// absolute path is requested as this is really slow
		// Should only ever be required for sqlplus @
		if (Paths.get(name).isAbsolute()) {
			if (absoluteResourceMap == null) {
				absoluteResourceMap = new HashMap<>();
				for (LoadableResource resource : resources) {
					absoluteResourceMap.put(resource.getAbsolutePathOnDisk().toLowerCase(Locale.ROOT), resource);
				}
			}
			loadedResource = absoluteResourceMap.get(name.toLowerCase(Locale.ROOT));
			if (loadedResource != null) {
				return loadedResource;
			}
		}
		return null;
	}

	/**
	 * Returns all known resources starting with the specified prefix and ending with any
	 * of the specified suffixes.
	 * @param prefix The prefix of the resource names to match.
	 * @param suffixes The suffixes of the resource names to match.
	 * @return The resources that were found.
	 */
	@Override
	public Collection<LoadableResource> getResources(String prefix, Collection<String> suffixes) {
		List<LoadableResource> result = new ArrayList<>();
		for (LoadableResource resource : resources) {
			String fileName = resource.getFilename();
			if (StringUtils.startsAndEndsWith(fileName, prefix, suffixes)) {
				result.add(resource);
			}
			else {
				LOG.debug("Filtering out resource: " + resource.getAbsolutePath() + " (filename: " + fileName + ")");
			}
		}
		return result;
	}

	/**
	 * Scans the classpath for concrete classes under the specified package implementing
	 * the specified interface. Non-instantiable abstract classes are filtered out.
	 * @return The non-abstract classes that were found.
	 */
	public Collection<Class<? extends I>> getClasses() {
		return Collections.unmodifiableCollection(classes);
	}

}
