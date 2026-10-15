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
package com.snaphop.flylite.internal.util;

import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A pair of non null values.
 *
 * @param <L> left type.
 * @param <R> right type.
 */
public final class Pair<L extends @NonNull Object, R extends @NonNull Object> {

	private final L left;

	private final R right;

	private Pair(L left, R right) {
		this.left = left;
		this.right = right;
	}

	public static <L extends @NonNull Object, R extends @NonNull Object> Pair<L, R> of(L left, R right) {
		return new Pair<>(left, right);
	}

	public L getLeft() {
		return left;
	}

	public R getRight() {
		return right;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		return o instanceof Pair<?, ?> pair && left.equals(pair.left) && right.equals(pair.right);
	}

	@Override
	public int hashCode() {
		return Objects.hash(left, right);
	}

}
