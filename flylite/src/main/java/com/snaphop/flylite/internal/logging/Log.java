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
package com.snaphop.flylite.internal.logging;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import org.jspecify.annotations.Nullable;

/**
 * Thin wrapper over {@link System.Logger} that keeps the call sites ported from Flyway
 * readable. Flylite has no logging dependencies: route {@code System.Logger} to whatever
 * logging framework the application uses.
 * <p>
 * Tools that run Flylite on a single thread (the Maven plugin) can temporarily redirect
 * the logging of the current thread with {@link #redirect(Sink)}.
 */
public final class Log {

	/**
	 * Receives log events instead of {@link System.Logger}.
	 */
	public interface Sink {

		/**
		 * Whether the level is enabled.
		 * @param level level.
		 * @return true if enabled.
		 */
		boolean isLoggable(Level level);

		/**
		 * Logs a message.
		 * @param level level.
		 * @param message message.
		 * @param throwable throwable or {@code null}.
		 */
		void log(Level level, String message, @Nullable Throwable throwable);

	}

	private static final ThreadLocal<@Nullable Sink> SINK = new ThreadLocal<>();

	private final Logger logger;

	private Log(Logger logger) {
		this.logger = logger;
	}

	/**
	 * Creates a logger named after the given class.
	 * @param type class used for the logger name.
	 * @return logger.
	 */
	public static Log of(Class<?> type) {
		return new Log(System.getLogger(type.getName()));
	}

	/**
	 * Redirects logging of the current thread to the sink until the returned handle is
	 * closed.
	 * @param sink sink.
	 * @return handle that restores the previous sink when closed.
	 */
	public static AutoCloseable redirect(Sink sink) {
		Sink previous = SINK.get();
		SINK.set(sink);
		return () -> {
			if (previous == null) {
				SINK.remove();
			}
			else {
				SINK.set(previous);
			}
		};
	}

	public boolean isDebugEnabled() {
		return isLoggable(Level.DEBUG);
	}

	private boolean isLoggable(Level level) {
		Sink sink = SINK.get();
		return sink != null ? sink.isLoggable(level) : logger.isLoggable(level);
	}

	private void log(Level level, String message, @Nullable Throwable throwable) {
		Sink sink = SINK.get();
		if (sink != null) {
			sink.log(level, message, throwable);
		}
		else if (throwable != null) {
			logger.log(level, message, throwable);
		}
		else {
			logger.log(level, message);
		}
	}

	public void debug(String message) {
		log(Level.DEBUG, message, null);
	}

	public void info(String message) {
		log(Level.INFO, message, null);
	}

	public void warn(String message) {
		log(Level.WARNING, message, null);
	}

	public void error(String message) {
		log(Level.ERROR, message, null);
	}

	public void error(String message, Throwable e) {
		log(Level.ERROR, message, e);
	}

}
