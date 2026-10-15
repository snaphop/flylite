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

public class Token {

	private final TokenType type;

	private final int pos;

	private final int line;

	private final int col;

	private final String text;

	private final int parensDepth;

	public Token(final TokenType type, final int pos, final int line, final int col, final String text,
			final String rawText, final int parensDepth) {
		this.type = type;
		this.pos = pos;
		this.line = line;
		this.col = col;
		this.text = text;
		this.parensDepth = parensDepth;
	}

	public TokenType getType() {
		return this.type;
	}

	public int getPos() {
		return this.pos;
	}

	public int getLine() {
		return this.line;
	}

	public int getCol() {
		return this.col;
	}

	public String getText() {
		return this.text;
	}

	public int getParensDepth() {
		return this.parensDepth;
	}

}
