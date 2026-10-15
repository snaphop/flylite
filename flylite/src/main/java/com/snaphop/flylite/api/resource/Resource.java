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
package com.snaphop.flylite.api.resource;

/**
 * A resource (such as a .sql file) used by Flylite.
 */
public interface Resource {

	/**
	 * Returns the absolute path and filename of the resource on the classpath or
	 * @return The absolute path and filename of the resource on the classpath or
	 * filesystem (path and filename).
	 */
	String getAbsolutePath();

	/**
	 * Returns the absolute path and filename of this resource on disk, regardless of
	 * @return The absolute path and filename of this resource on disk, regardless of
	 * whether this resources points at the classpath or filesystem.
	 */
	String getAbsolutePathOnDisk();

	/**
	 * Returns the filename of this resource, without the path.
	 * @return The filename of this resource, without the path.
	 */
	String getFilename();

	/**
	 * Returns the filename of this resource, as well as the path relative to the location
	 * @return The filename of this resource, as well as the path relative to the location
	 * where the resource was loaded from.
	 */
	String getRelativePath();

}