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
package com.snaphop.flylite.maven;

import java.io.File;
import java.lang.System.Logger.Level;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.Flylite;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.Location;
import com.snaphop.flylite.api.configuration.FluentConfiguration;
import com.snaphop.flylite.internal.logging.Log;

/**
 * Common base class for all mojos with all common attributes.
 * <p>
 * Every parameter can also be set with a {@code flylite.} prefixed user property, for
 * example {@code -Dflylite.url=jdbc:postgresql:db}.
 */
abstract class AbstractFlyliteMojo extends AbstractMojo {

	/**
	 * Whether to skip the execution of the Maven Plugin for this module.
	 */
	@Parameter(property = "flylite.skip", defaultValue = "false")
	boolean skip;

	/**
	 * The fully qualified classname of the JDBC driver to use to connect to the database.
	 * By default this is auto-detected from the url.
	 */
	@Parameter(property = "flylite.driver")
	@Nullable String driver;

	/**
	 * The JDBC url to use to connect to the database.
	 */
	@Parameter(property = "flylite.url")
	@Nullable String url;

	/**
	 * The user to use to connect to the database.
	 */
	@Parameter(property = "flylite.user")
	@Nullable String user;

	/**
	 * The password to use to connect to the database.
	 */
	@Parameter(property = "flylite.password")
	@Nullable String password;

	/**
	 * Extra JDBC driver properties.
	 */
	@Parameter
	@Nullable Map<String, String> jdbcProperties;

	/**
	 * The maximum number of retries when attempting to connect to the database.
	 */
	@Parameter(property = "flylite.connectRetries")
	@Nullable Integer connectRetries;

	/**
	 * The maximum time between retries when attempting to connect to the database in
	 * seconds.
	 */
	@Parameter(property = "flylite.connectRetriesInterval")
	@Nullable Integer connectRetriesInterval;

	/**
	 * SQL statements to run to initialize a new database connection immediately after
	 * opening it.
	 */
	@Parameter(property = "flylite.initSql")
	@Nullable String initSql;

	/**
	 * The default schema managed by Flylite.
	 */
	@Parameter(property = "flylite.defaultSchema")
	@Nullable String defaultSchema;

	/**
	 * The schemas managed by Flylite.
	 */
	@Parameter(property = "flylite.schemas")
	String[] schemas = new String[0];

	/**
	 * Whether Flylite should attempt to create the configured schemas.
	 */
	@Parameter(property = "flylite.createSchemas")
	@Nullable Boolean createSchemas;

	/**
	 * The name of the schema history table. Defaults to {@code flyway_schema_history} for
	 * compatibility with Flyway.
	 */
	@Parameter(property = "flylite.table")
	@Nullable String table;

	/**
	 * The tablespace where to create the schema history table.
	 */
	@Parameter(property = "flylite.tablespace")
	@Nullable String tablespace;

	/**
	 * The version to tag an existing schema with when executing baseline.
	 */
	@Parameter(property = "flylite.baselineVersion")
	@Nullable String baselineVersion;

	/**
	 * The description to tag an existing schema with when executing baseline.
	 */
	@Parameter(property = "flylite.baselineDescription")
	@Nullable String baselineDescription;

	/**
	 * Whether to automatically call baseline when migrate is executed against a non-empty
	 * schema with no schema history table.
	 */
	@Parameter(property = "flylite.baselineOnMigrate")
	@Nullable Boolean baselineOnMigrate;

	/**
	 * Locations to scan recursively for migrations. Locations starting with
	 * {@code filesystem:} are relative to the working directory. Defaults to
	 * {@code filesystem:src/main/resources/db/migration}.
	 */
	@Parameter(property = "flylite.locations")
	String[] locations = new String[0];

	/**
	 * The encoding of SQL migrations.
	 */
	@Parameter(property = "flylite.encoding", defaultValue = "${project.build.sourceEncoding}")
	@Nullable String encoding;

	/**
	 * The file name prefix for versioned SQL migrations.
	 */
	@Parameter(property = "flylite.sqlMigrationPrefix")
	@Nullable String sqlMigrationPrefix;

	/**
	 * The file name prefix for repeatable SQL migrations.
	 */
	@Parameter(property = "flylite.repeatableSqlMigrationPrefix")
	@Nullable String repeatableSqlMigrationPrefix;

	/**
	 * The file name separator for SQL migrations.
	 */
	@Parameter(property = "flylite.sqlMigrationSeparator")
	@Nullable String sqlMigrationSeparator;

	/**
	 * The file name suffixes for SQL migrations.
	 */
	@Parameter(property = "flylite.sqlMigrationSuffixes")
	String[] sqlMigrationSuffixes = new String[0];

	/**
	 * Whether SQL migrations run within a transaction.
	 */
	@Parameter(property = "flylite.executeInTransaction")
	@Nullable Boolean executeInTransaction;

	/**
	 * Whether to allow mixing transactional and non-transactional statements within the
	 * same migration.
	 */
	@Parameter(property = "flylite.mixed")
	@Nullable Boolean mixed;

	/**
	 * Whether to group all pending migrations together in the same transaction.
	 */
	@Parameter(property = "flylite.group")
	@Nullable Boolean group;

	/**
	 * The username recorded in the schema history table.
	 */
	@Parameter(property = "flylite.installedBy")
	@Nullable String installedBy;

	/**
	 * Whether clean is disabled. Clean is disabled by default.
	 */
	@Parameter(property = "flylite.cleanDisabled")
	@Nullable Boolean cleanDisabled;

	/**
	 * Allows migrations to be run "out of order".
	 */
	@Parameter(property = "flylite.outOfOrder")
	@Nullable Boolean outOfOrder;

	/**
	 * The target version up to which Flylite should consider migrations. Either a
	 * version, {@code latest} or {@code current}.
	 */
	@Parameter(property = "flylite.target")
	@Nullable String target;

	/**
	 * Patterns of migration states to ignore when validating, for example
	 * {@code *:missing} or {@code *:future}.
	 */
	@Parameter(property = "flylite.ignoreMigrationPatterns")
	String[] ignoreMigrationPatterns = new String[0];

	/**
	 * Whether to fail if a location contains a file that does not follow the migration
	 * naming convention.
	 */
	@Parameter(property = "flylite.validateMigrationNaming")
	@Nullable Boolean validateMigrationNaming;

	/**
	 * Whether to automatically call validate when running migrate.
	 */
	@Parameter(property = "flylite.validateOnMigrate")
	@Nullable Boolean validateOnMigrate;

	/**
	 * Whether placeholders should be replaced.
	 */
	@Parameter(property = "flylite.placeholderReplacement")
	@Nullable Boolean placeholderReplacement;

	/**
	 * The placeholders to replace in SQL migrations.
	 */
	@Parameter
	@Nullable Map<String, String> placeholders;

	/**
	 * The prefix of every placeholder.
	 */
	@Parameter(property = "flylite.placeholderPrefix")
	@Nullable String placeholderPrefix;

	/**
	 * The suffix of every placeholder.
	 */
	@Parameter(property = "flylite.placeholderSuffix")
	@Nullable String placeholderSuffix;

	/**
	 * The separator of default placeholders.
	 */
	@Parameter(property = "flylite.placeholderSeparator")
	@Nullable String placeholderSeparator;

	/**
	 * The prefix of every script placeholder.
	 */
	@Parameter(property = "flylite.scriptPlaceholderPrefix")
	@Nullable String scriptPlaceholderPrefix;

	/**
	 * The suffix of every script placeholder.
	 */
	@Parameter(property = "flylite.scriptPlaceholderSuffix")
	@Nullable String scriptPlaceholderSuffix;

	/**
	 * Whether the SQL callbacks found in the locations should be skipped.
	 */
	@Parameter(property = "flylite.skipDefaultCallbacks")
	@Nullable Boolean skipDefaultCallbacks;

	/**
	 * The maximum number of retries when trying to obtain a lock.
	 */
	@Parameter(property = "flylite.lockRetryCount")
	@Nullable Integer lockRetryCount;

	/**
	 * Whether to fail if a location does not exist.
	 */
	@Parameter(property = "flylite.failOnMissingLocations")
	@Nullable Boolean failOnMissingLocations;

	/**
	 * Whether PostgreSQL uses a transaction scoped advisory lock.
	 */
	@Parameter(property = "flylite.postgresqlTransactionalLock")
	@Nullable Boolean postgresqlTransactionalLock;

	/**
	 * The working directory used to resolve relative {@code filesystem:} locations.
	 * Defaults to the project base directory.
	 */
	@Parameter(property = "flylite.workingDirectory")
	@Nullable File workingDirectory;

	/**
	 * The Maven project this plugin runs in.
	 */
	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	@SuppressWarnings("NullAway.Init")
	MavenProject mavenProject;

	@Override
	public final void execute() throws MojoExecutionException {
		if (skip) {
			getLog().info("Skipping Flylite execution");
			return;
		}
		try (AutoCloseable redirect = Log.redirect(new MavenLogSink(getLog()));
				URLClassLoader classLoader = projectClassLoader()) {
			doExecute(configure(classLoader).load());
		}
		catch (FlyliteException e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}
		catch (MojoExecutionException e) {
			throw e;
		}
		catch (Exception e) {
			throw new MojoExecutionException(e.toString(), e);
		}
	}

	/**
	 * Runs the command.
	 * @param flylite configured flylite.
	 * @throws Exception if the command fails.
	 */
	abstract void doExecute(Flylite flylite) throws Exception;

	FluentConfiguration configure(ClassLoader classLoader) {
		FluentConfiguration c = Flylite.configure(classLoader);
		String u = url;
		if (u != null && !u.isBlank()) {
			c.dataSource(u, user, password);
		}
		c.driver(driver);
		if (jdbcProperties != null) {
			c.jdbcProperties(nonNullValues(jdbcProperties));
		}
		if (connectRetries != null) {
			c.connectRetries(connectRetries);
		}
		if (connectRetriesInterval != null) {
			c.connectRetriesInterval(connectRetriesInterval);
		}
		c.initSql(initSql);
		c.defaultSchema(defaultSchema);
		if (schemas.length > 0) {
			c.schemas(schemas);
		}
		if (createSchemas != null) {
			c.createSchemas(createSchemas);
		}
		if (table != null) {
			c.table(table);
		}
		c.tablespace(tablespace);
		if (baselineVersion != null) {
			c.baselineVersion(baselineVersion);
		}
		if (baselineDescription != null) {
			c.baselineDescription(baselineDescription);
		}
		if (baselineOnMigrate != null) {
			c.baselineOnMigrate(baselineOnMigrate);
		}
		c.locations(resolveLocations());
		if (encoding != null) {
			c.encoding(encoding);
		}
		if (sqlMigrationPrefix != null) {
			c.sqlMigrationPrefix(sqlMigrationPrefix);
		}
		if (repeatableSqlMigrationPrefix != null) {
			c.repeatableSqlMigrationPrefix(repeatableSqlMigrationPrefix);
		}
		if (sqlMigrationSeparator != null) {
			c.sqlMigrationSeparator(sqlMigrationSeparator);
		}
		if (sqlMigrationSuffixes.length > 0) {
			c.sqlMigrationSuffixes(sqlMigrationSuffixes);
		}
		if (executeInTransaction != null) {
			c.executeInTransaction(executeInTransaction);
		}
		if (mixed != null) {
			c.mixed(mixed);
		}
		if (group != null) {
			c.group(group);
		}
		c.installedBy(installedBy);
		if (cleanDisabled != null) {
			c.cleanDisabled(cleanDisabled);
		}
		if (outOfOrder != null) {
			c.outOfOrder(outOfOrder);
		}
		if (target != null) {
			c.target(target);
		}
		if (ignoreMigrationPatterns.length > 0) {
			c.ignoreMigrationPatterns(ignoreMigrationPatterns);
		}
		if (validateMigrationNaming != null) {
			c.validateMigrationNaming(validateMigrationNaming);
		}
		if (validateOnMigrate != null) {
			c.validateOnMigrate(validateOnMigrate);
		}
		if (placeholderReplacement != null) {
			c.placeholderReplacement(placeholderReplacement);
		}
		if (placeholders != null) {
			c.placeholders(nonNullValues(placeholders));
		}
		if (placeholderPrefix != null) {
			c.placeholderPrefix(placeholderPrefix);
		}
		if (placeholderSuffix != null) {
			c.placeholderSuffix(placeholderSuffix);
		}
		if (placeholderSeparator != null) {
			c.placeholderSeparator(placeholderSeparator);
		}
		if (scriptPlaceholderPrefix != null) {
			c.scriptPlaceholderPrefix(scriptPlaceholderPrefix);
		}
		if (scriptPlaceholderSuffix != null) {
			c.scriptPlaceholderSuffix(scriptPlaceholderSuffix);
		}
		if (skipDefaultCallbacks != null) {
			c.skipDefaultCallbacks(skipDefaultCallbacks);
		}
		if (lockRetryCount != null) {
			c.lockRetryCount(lockRetryCount);
		}
		if (failOnMissingLocations != null) {
			c.failOnMissingLocations(failOnMissingLocations);
		}
		if (postgresqlTransactionalLock != null) {
			c.postgresqlTransactionalLock(postgresqlTransactionalLock);
		}
		return c;
	}

	private static Map<String, String> nonNullValues(Map<String, String> map) {
		Map<String, String> result = new java.util.LinkedHashMap<>();
		for (var e : map.entrySet()) {
			String value = e.getValue();
			result.put(e.getKey(), value == null ? "" : value);
		}
		return result;
	}

	private File workDir() {
		File base = mavenProject.getBasedir();
		File wd = workingDirectory;
		if (wd == null) {
			return base;
		}
		return wd.isAbsolute() ? wd : new File(base, wd.getPath());
	}

	private String[] resolveLocations() {
		File workDir = workDir();
		String[] ls = locations;
		if (ls.length == 0) {
			return new String[] { Location.FILESYSTEM_PREFIX
					+ new File(workDir, "src/main/resources/db/migration").getAbsolutePath() };
		}
		String[] resolved = new String[ls.length];
		for (int i = 0; i < ls.length; i++) {
			String l = ls[i].trim();
			if (l.startsWith(Location.FILESYSTEM_PREFIX)) {
				String path = l.substring(Location.FILESYSTEM_PREFIX.length());
				File file = new File(path);
				if (!file.isAbsolute()) {
					file = new File(workDir, path);
				}
				resolved[i] = Location.FILESYSTEM_PREFIX + file.getAbsolutePath();
			}
			else {
				resolved[i] = l;
			}
		}
		return resolved;
	}

	/**
	 * Class loader with the project's classes and dependencies so that classpath
	 * locations, Java migrations and project JDBC drivers can be found. The plugin's own
	 * dependencies (for example a JDBC driver added to the plugin) are visible through
	 * the parent.
	 */
	private URLClassLoader projectClassLoader() throws DependencyResolutionRequiredException, MalformedURLException {
		Set<String> elements = new LinkedHashSet<>();
		elements.addAll(mavenProject.getCompileClasspathElements());
		elements.addAll(mavenProject.getRuntimeClasspathElements());
		List<URL> urls = new ArrayList<>();
		for (String element : elements) {
			urls.add(new File(element).toURI().toURL());
		}
		return new URLClassLoader(urls.toArray(URL[]::new), getClass().getClassLoader());
	}

	private record MavenLogSink(org.apache.maven.plugin.logging.Log log) implements Log.Sink {

		@Override
		public boolean isLoggable(Level level) {
			return switch (level) {
				case ALL, TRACE, DEBUG -> log.isDebugEnabled();
				case INFO -> log.isInfoEnabled();
				case WARNING -> log.isWarnEnabled();
				case ERROR -> log.isErrorEnabled();
				case OFF -> false;
			};
		}

		@Override
		public void log(Level level, String message, @Nullable Throwable throwable) {
			switch (level) {
				case ALL, TRACE, DEBUG -> log.debug(message, throwable);
				case INFO -> log.info(message, throwable);
				case WARNING -> log.warn(message, throwable);
				case ERROR -> log.error(message, throwable);
				case OFF -> {
				}
			}
		}

	}

}
