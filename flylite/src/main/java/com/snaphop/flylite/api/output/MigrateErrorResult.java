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
package com.snaphop.flylite.api.output;

import com.snaphop.flylite.api.ErrorCode;
import com.snaphop.flylite.api.FlyliteException;

/**
 * The result of a failed migrate containing what was migrated before the failure.
 */
public final class MigrateErrorResult extends MigrateResult {

	/**
	 * The error code of the failure.
	 */
	public ErrorCode errorCode;

	/**
	 * The error message of the failure.
	 */
	public String errorMessage;

	public MigrateErrorResult(MigrateResult migrateResult, Exception e) {
		super(migrateResult);
		this.success = false;
		this.errorCode = e instanceof FlyliteException fe ? fe.getErrorCode() : ErrorCode.FAULT;
		String message = e.getMessage();
		this.errorMessage = message == null ? "Error occurred" : message;
	}

}
