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
package com.snaphop.flylite.internal.resource.filesystem;

import com.snaphop.flylite.internal.logging.Log;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.Location;
import com.snaphop.flylite.api.resource.LoadableResource;
import com.snaphop.flylite.internal.util.BomStrippingReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.StandardOpenOption;

import org.jspecify.annotations.Nullable;

public class FileSystemResource extends LoadableResource {

	private static final Log LOG = Log.of(FileSystemResource.class);

	private final File file;

	private final String relativePath;

	private final Charset encoding;

	public FileSystemResource(@Nullable Location location, String fileNameWithPath, Charset encoding) {
		this.file = new File(new File(fileNameWithPath).getPath());
		this.relativePath = location == null ? file.getPath()
				: location.getPathRelativeToThis(file.getPath()).replace("\\", "/");
		this.encoding = encoding;
	}

	@Override
	public String getAbsolutePath() {
		return file.getPath();
	}

	@Override
	public String getAbsolutePathOnDisk() {
		return file.getAbsolutePath();
	}

	@Override
	public Reader read() {
		Charset charSet = encoding;
		try {
			return Channels.newReader(FileChannel.open(file.toPath(), StandardOpenOption.READ), charSet.newDecoder(),
					4096);
		}
		catch (IOException e) {
			LOG.debug("Unable to load filesystem resource" + file.getPath() + " using FileChannel.open."
					+ " Falling back to FileInputStream implementation. Exception message: " + e.getMessage());
		}
		try {
			return new BufferedReader(
					new BomStrippingReader(new InputStreamReader(new FileInputStream(file), charSet)));
		}
		catch (IOException e) {
			throw new FlyliteException(
					"Unable to load filesystem resource: " + file.getPath() + " (encoding: " + charSet + ")", e);
		}
	}

	/**
	 * @return The filename of this resource, without the path.
	 */
	@Override
	public String getFilename() {
		return file.getName();
	}

	@Override
	public String getRelativePath() {
		return relativePath;
	}

}
