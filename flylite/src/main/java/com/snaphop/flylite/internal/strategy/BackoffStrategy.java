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
package com.snaphop.flylite.internal.strategy;

public class BackoffStrategy {

	private int current;

	private final int exponent;

	private final int interval;

	/**
	 * Returns the current value of the counter and immediately updates it with the next
	 * @return The current value of the counter and immediately updates it with the next
	 * value
	 */
	public int next() {
		int temp = current;
		current = Math.min(current * exponent, interval);
		return temp;
	}

	/**
	 * Returns the current value of the counter without updating it
	 * @return The current value of the counter without updating it
	 */
	public int peek() {
		return current;
	}

	public BackoffStrategy(final int current, final int exponent, final int interval) {
		this.current = current;
		this.exponent = exponent;
		this.interval = interval;
	}

}
