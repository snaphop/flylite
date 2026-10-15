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
package com.snaphop.flylite.internal.exception;

import com.snaphop.flylite.api.ErrorCode;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.internal.util.ExceptionUtils;
import com.snaphop.flylite.internal.util.StringUtils;

import java.sql.SQLException;

/**
 * The specific exception thrown when Flylite encounters a problem in SQL statement.
 */
public class FlyliteSqlException extends FlyliteException {

	public FlyliteSqlException(String message, SQLException sqlException) {
		super(message, sqlException, ErrorCode.DB_CONNECTION);
	}

	@Override
	public String getMessage() {
		String title = String.valueOf(super.getMessage());
		String underline = StringUtils.trimOrPad("", title.length(), '-');
		String details = getCause() instanceof SQLException e ? ExceptionUtils.toMessage(e) : "";
		return title + "\n" + underline + "\n" + details;
	}

}