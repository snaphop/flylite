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
package com.snaphop.flylite.api;

/**
 * Exception thrown when Flylite encounters a problem.
 */
public class FlyliteException extends RuntimeException {

	private ErrorCode errorCode = ErrorCode.ERROR;

	/**
	 * Creates a new FlyliteException with this message, cause, and error code.
	 * @param message The exception message.
	 * @param cause The exception cause.
	 * @param errorCode The error code.
	 */
	public FlyliteException(String message, Throwable cause, ErrorCode errorCode) {
		super(message, cause);
		this.errorCode = errorCode;
	}

	/**
	 * Creates a new FlyliteException with this message and error code
	 * @param message The exception message.
	 * @param errorCode The error code.
	 */
	public FlyliteException(String message, ErrorCode errorCode) {
		super(message);
		this.errorCode = errorCode;
	}

	/**
	 * Creates a new FlyliteException with this message and this cause.
	 * @param message The exception message.
	 * @param cause The exception cause.
	 */
	public FlyliteException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Creates a new FlyliteException with this cause. For use in subclasses that override
	 * getMessage().
	 * @param cause The exception cause.
	 */
	public FlyliteException(Throwable cause) {
		super(cause);
	}

	/**
	 * Creates a new FlyliteException with this message.
	 * @param message The exception message.
	 */
	public FlyliteException(String message) {
		super(message);
	}

	/**
	 * Creates a new FlyliteException. For use in subclasses that override getMessage().
	 */
	public FlyliteException() {
		super();
	}

	public ErrorCode getErrorCode() {
		return this.errorCode;
	}

}
