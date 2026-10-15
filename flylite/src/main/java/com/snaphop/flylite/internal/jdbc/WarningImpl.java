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
package com.snaphop.flylite.internal.jdbc;

public class WarningImpl implements Warning {

	private final int code;

	private final String state;

	private final String message;

	private boolean handled;

	public WarningImpl(final int code, final String state, final String message) {
		this.code = code;
		this.state = state;
		this.message = message;
	}

	@Override
	public int getCode() {
		return this.code;
	}

	@Override
	public String getState() {
		return this.state;
	}

	@Override
	public String getMessage() {
		return this.message;
	}

	@Override
	public boolean isHandled() {
		return this.handled;
	}

	@Override
	public void setHandled(final boolean handled) {
		this.handled = handled;
	}

}
