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

import java.lang.reflect.Modifier;
import java.security.CodeSource;
import java.security.ProtectionDomain;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.api.FlyliteException;

public final class ClassUtils {

	private ClassUtils() {
	}

	/**
	 * Creates a new instance of this class.
	 * @param className the fully qualified name of the class to instantiate.
	 * @param classLoader the ClassLoader to use.
	 * @param type the expected type.
	 * @param <T> the expected type.
	 * @return the new instance.
	 * @throws FlyliteException thrown when the instantiation failed.
	 */
	public static <T> T instantiate(String className, ClassLoader classLoader, Class<T> type) {
		try {
			return type.cast(Class.forName(className, true, classLoader).getDeclaredConstructor().newInstance());
		}
		catch (Exception e) {
			throw new FlyliteException("Unable to instantiate class " + className + " : " + e.getMessage(), e);
		}
	}

	/**
	 * Creates a new instance of this class.
	 * @param clazz the class to instantiate.
	 * @param <T> the type.
	 * @return the new instance.
	 * @throws FlyliteException thrown when the instantiation failed.
	 */
	public static <T> T instantiate(Class<T> clazz) {
		try {
			return clazz.getDeclaredConstructor().newInstance();
		}
		catch (Exception e) {
			throw new FlyliteException("Unable to instantiate class " + clazz.getName() + " : " + e.getMessage(), e);
		}
	}

	/**
	 * Loads the class with this name using the class loader.
	 * @param implementedInterface the interface the class is expected to implement.
	 * @param className the name of the class to load.
	 * @param classLoader the ClassLoader to use.
	 * @param <I> the interface type.
	 * @return the newly loaded class or {@code null} if it is not a concrete
	 * implementation.
	 * @throws Exception if the class could not be loaded or instantiated.
	 */
	@SuppressWarnings("unchecked")
	public static <I> @Nullable Class<? extends I> loadClass(Class<I> implementedInterface, String className,
			ClassLoader classLoader) throws Exception {
		Class<?> clazz = classLoader.loadClass(className);
		if (!implementedInterface.isAssignableFrom(clazz)) {
			return null;
		}
		if (Modifier.isAbstract(clazz.getModifiers()) || clazz.isEnum() || clazz.isAnonymousClass()) {
			return null;
		}
		clazz.getDeclaredConstructor().newInstance();
		return (Class<? extends I>) clazz;
	}

	public static String formatThrowable(Throwable e) {
		return "(" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
	}

	/**
	 * Retrieves the physical location on disk of this class.
	 * @param aClass the class to get the location for.
	 * @return the absolute path of the .class file or {@code null} if unknown.
	 */
	public static @Nullable String getLocationOnDisk(Class<?> aClass) {
		ProtectionDomain protectionDomain = aClass.getProtectionDomain();
		if (protectionDomain == null) {
			return null;
		}
		CodeSource codeSource = protectionDomain.getCodeSource();
		if (codeSource == null || codeSource.getLocation() == null) {
			// Custom classloader with for example classes defined using
			// URLClassLoader#defineClass(String name, byte[] b, int off, int len)
			return null;
		}
		return UrlUtils.decodeURL(codeSource.getLocation().getPath());
	}

}
