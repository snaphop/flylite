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
package com.snaphop.flylite.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.snaphop.flylite.Flylite;

/**
 * Drops all database objects in the configured schemas. Clean is disabled unless
 * {@code cleanDisabled} is set to {@code false}.
 */
@Mojo(name = "clean", requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.CLEAN,
		threadSafe = true)
public class CleanMojo extends AbstractFlyliteMojo {

	@Override
	void doExecute(Flylite flylite) {
		flylite.clean();
	}

}
