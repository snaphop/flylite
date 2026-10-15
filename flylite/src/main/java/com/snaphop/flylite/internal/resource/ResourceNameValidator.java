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

import com.snaphop.flylite.internal.logging.Log;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.ResourceProvider;
import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.api.resource.LoadableResource;
import com.snaphop.flylite.api.resource.Resource;
import com.snaphop.flylite.internal.util.StringUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ResourceNameValidator {

	private static final Log LOG = Log.of(ResourceNameValidator.class);

	/**
	 * Validates the names of all SQL resources returned by the ResourceProvider
	 * @param provider The ResourceProvider to validate
	 * @param configuration The configuration to use
	 */
	public void validateSQLMigrationNaming(ResourceProvider provider, Configuration configuration) {
		List<String> errorsFound = new ArrayList<>();
		ResourceNameParser resourceNameParser = new ResourceNameParser(configuration);
		for (Resource resource : getAllSqlResources(provider, configuration)) {
			String filename = resource.getFilename();
			LOG.debug("Validating " + filename);
			ResourceName result = resourceNameParser.parse(filename);
			if (!result.isValid()) {
				errorsFound.add(result.getValidityMessage());
			}
		}
		if (!errorsFound.isEmpty()) {
			if (configuration.isValidateMigrationNaming()) {
				throw new FlyliteException("Invalid SQL filenames found:\r\n"
						+ StringUtils.collectionToDelimitedString(errorsFound, "\r\n"));
			}
			else {
				LOG.info(errorsFound.size()
						+ " SQL migrations were detected but not run because they did not follow the filename convention.");
				LOG.info(
						"If this is in error, set \'validateMigrationNaming\' to true to fail fast and see a list of the invalid file names.");
			}
		}
	}

	private Collection<LoadableResource> getAllSqlResources(ResourceProvider provider, Configuration configuration) {
		return provider.getResources("", configuration.getSqlMigrationSuffixes());
	}

}
