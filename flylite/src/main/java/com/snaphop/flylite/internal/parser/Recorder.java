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
package com.snaphop.flylite.internal.parser;

/**
 * Records the characters of the statement being parsed.
 */
public class Recorder {

	private StringBuilder recorder = new StringBuilder();

	private boolean running = false;

	private int recorderConfirmedPos = 0;

	public void record(char c) {
		if (running) {
			recorder.append(c);
		}
	}

	public int length() {
		return recorder.length();
	}

	public void start() {
		recorder = new StringBuilder();
		recorderConfirmedPos = 0;
		running = true;
	}

	public void record(String str) {
		recorder.append(str);
		confirm();
	}

	public void confirm() {
		recorderConfirmedPos = recorder.length();
	}

	public String stop() {
		// Drop unconfirmed parts of recording
		recorder.delete(recorderConfirmedPos, recorder.length());
		String result = recorder.toString();
		recorder = new StringBuilder();
		running = false;
		return result;
	}

}
