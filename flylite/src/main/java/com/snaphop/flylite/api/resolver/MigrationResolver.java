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
package com.snaphop.flylite.api.resolver;

import com.snaphop.flylite.api.ResourceProvider;
import com.snaphop.flylite.api.configuration.Configuration;
import com.snaphop.flylite.internal.sqlscript.SqlScriptExecutorFactory;
import com.snaphop.flylite.internal.sqlscript.SqlScriptFactory;
import java.util.Collection;

/**
 * Resolves available migrations. This interface can be implemented to create custom
 * resolvers. A custom resolver can be used to create additional types of migrations not
 * covered by the standard resolvers (jdbc, sql). Using the skipDefaultResolvers
 * configuration property, the built-in resolvers can also be completely replaced.
 */
public interface MigrationResolver {

	class Context {

		public final Configuration configuration;

		public final ResourceProvider resourceProvider;

		public final SqlScriptFactory sqlScriptFactory;

		public final SqlScriptExecutorFactory sqlScriptExecutorFactory;

		public Context(final Configuration configuration, final ResourceProvider resourceProvider,
				final SqlScriptFactory sqlScriptFactory, final SqlScriptExecutorFactory sqlScriptExecutorFactory) {
			this.configuration = configuration;
			this.resourceProvider = resourceProvider;
			this.sqlScriptFactory = sqlScriptFactory;
			this.sqlScriptExecutorFactory = sqlScriptExecutorFactory;
		}

	}

	Collection<ResolvedMigration> resolveMigrations(Context context);

}
