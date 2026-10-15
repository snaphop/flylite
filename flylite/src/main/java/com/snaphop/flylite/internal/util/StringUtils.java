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

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StringUtils {

	/**
	 * Trims or pads (with spaces) this string, so it has this exact length.
	 * @param str The string to adjust. {@code null} is treated as an empty string.
	 * @param length The exact length to reach.
	 * @return The adjusted string.
	 */
	public static String trimOrPad(String str, int length) {
		return trimOrPad(str, length, ' ');
	}

	/**
	 * Trims or pads this string, so it has this exact length.
	 * @param str The string to adjust. {@code null} is treated as an empty string.
	 * @param length The exact length to reach.
	 * @param padChar The padding character.
	 * @return The adjusted string.
	 */
	public static String trimOrPad(String str, int length, char padChar) {
		StringBuilder result;
		if (str == null) {
			result = new StringBuilder();
		}
		else {
			result = new StringBuilder(str);
		}
		if (result.length() > length) {
			return result.substring(0, length);
		}
		while (result.length() < length) {
			result.append(padChar);
		}
		return result.toString();
	}

	/**
	 * Trims or pads this string, so it has this exact length.
	 * @param str The string to adjust. {@code null} is treated as an empty string.
	 * @param length The exact length to reach.
	 * @param padChar The padding character.
	 * @return The adjusted string.
	 */
	public static String trimOrLeftPad(String str, int length, char padChar) {
		if (str == null) {
			str = "";
		}
		if (str.length() > length) {
			return str.substring(0, length);
		}
		return leftPad(str, length, padChar);
	}

	public static String leftPad(String original, int length, char padChar) {
		StringBuilder result = new StringBuilder(original);
		while (result.length() < length) {
			result.insert(0, padChar);
		}
		return result.toString();
	}

	/**
	 * Returns the first n characters from this string, where n = count. If the string is
	 * shorter, the entire string will be returned. If the string is longer, it will be
	 * truncated.
	 * @param str The string to parse.
	 * @param count The amount of characters to return.
	 * @return The first n characters from this string, where n = count.
	 */
	public static String left(String str, int count) {
		if (str.length() < count) {
			return str;
		}
		return str.substring(0, count);
	}

	/**
	 * Replaces all occurrences of this originalToken in this string with this
	 * replacementToken.
	 * @param str The string to process.
	 * @param originalToken The token to replace.
	 * @param replacementToken The replacement.
	 * @return The transformed str.
	 */
	public static String replaceAll(String str, String originalToken, String replacementToken) {
		return str.replaceAll(Pattern.quote(originalToken), Matcher.quoteReplacement(replacementToken));
	}

	/**
	 * Checks whether this string is not {@code null} and not <i>empty</i>.
	 * @param str The string to check.
	 * @return {@code true} if it has content, {@code false} if it is {@code null} or
	 * blank.
	 */
	public static boolean hasLength(@Nullable String str) {
		return str != null && str.length() > 0;
	}

	/**
	 * Turns this string array in one comma-delimited string.
	 * @param strings The array to process.
	 * @return The new comma-delimited string. An empty string if {@code strings} is
	 * empty. {@code null} if strings is {@code null}.
	 */
	public static String arrayToCommaDelimitedString(Object[] strings) {
		return arrayToDelimitedString(",", strings);
	}

	/**
	 * Turns this string array in one delimited string.
	 * @param delimiter The delimiter to use.
	 * @param strings The array to process.
	 * @return The new delimited string. An empty string if {@code strings} is empty.
	 * {@code null} if strings is {@code null}.
	 */
	public static String arrayToDelimitedString(String delimiter, Object[] strings) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < strings.length; i++) {
			if (i > 0) {
				builder.append(delimiter);
			}
			builder.append(strings[i]);
		}
		return builder.toString();
	}

	/**
	 * Checks whether this string isn't {@code null} and contains at least one non-blank
	 * character.
	 * @param s The string to check.
	 * @return {@code true} if it has text, {@code false} if not.
	 */
	public static boolean hasText(@Nullable String s) {
		return (s != null) && (s.trim().length() > 0);
	}

	/**
	 * Splits this string into a collection using this delimiter.
	 * @param str The string to split.
	 * @param delimiter The delimiter to use.
	 * @return The resulting array.
	 */
	public static List<String> tokenizeToStringCollection(String str, String delimiter) {
		return Arrays.stream(str.split(delimiter)).map(String::trim).collect(Collectors.toList());
	}

	/**
	 * Convenience method to return a Collection as a comma-delimited String. e.g. useful
	 * for {@code toString()} implementations.
	 * @param collection the Collection to analyse
	 * @return The comma-delimited String.
	 */
	public static String collectionToCommaDelimitedString(Collection<?> collection) {
		return collectionToDelimitedString(collection, ", ");
	}

	/**
	 * Convenience method to return a Collection as a delimited String. E.g. useful for
	 * {@code toString()} implementations.
	 * @param collection the Collection to analyse
	 * @param delimiter The delimiter.
	 * @return The delimited String.
	 */
	public static String collectionToDelimitedString(Collection<?> collection, String delimiter) {
		if (collection == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		Iterator<?> it = collection.iterator();
		while (it.hasNext()) {
			sb.append(it.next());
			if (it.hasNext()) {
				sb.append(delimiter);
			}
		}
		return sb.toString();
	}

	/**
	 * Checks whether this strings both begins with this prefix and ends withs either of
	 * these suffixes.
	 * @param str The string to check.
	 * @param prefix The prefix.
	 * @param suffixes The suffixes.
	 * @return {@code true} if it does, {@code false} if not.
	 */
	public static boolean startsAndEndsWith(String str, String prefix, Collection<String> suffixes) {
		if (StringUtils.hasLength(prefix) && !str.startsWith(prefix)) {
			return false;
		}
		for (String suffix : suffixes) {
			if (str.toUpperCase(Locale.ROOT).endsWith(suffix.toUpperCase(Locale.ROOT))
					&& (str.length() > (prefix + suffix).length())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks whether this character matches any of these characters.
	 * @param c The char to check.
	 * @param chars The chars that should match.
	 * @return {@code true} if it does, {@code false if not}.
	 */
	public static boolean isCharAnyOf(char c, String chars) {
		for (int i = 0; i < chars.length(); i++) {
			if (chars.charAt(i) == c) {
				return true;
			}
		}
		return false;
	}

	public static Pair<String, String> splitAtFirstSeparator(String input, String separator) {
		int separatorIndex = input.indexOf(separator);
		if (separatorIndex >= 0) {
			return Pair.of(input.substring(0, separatorIndex), input.substring(separatorIndex + separator.length()));
		}
		else {
			return Pair.of(input, "");
		}
	}

	private StringUtils() {
	}

}
