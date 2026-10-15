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
package com.snaphop.flylite.api.configuration;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.Flylite;
import com.snaphop.flylite.api.ClassProvider;
import com.snaphop.flylite.api.ErrorCode;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.Location;
import com.snaphop.flylite.api.MigrationVersion;
import com.snaphop.flylite.api.ResourceProvider;
import com.snaphop.flylite.api.callback.Callback;
import com.snaphop.flylite.api.migration.JavaMigration;
import com.snaphop.flylite.api.pattern.ValidatePattern;
import com.snaphop.flylite.api.resolver.MigrationResolver;
import com.snaphop.flylite.internal.jdbc.DriverDataSource;

/**
 * Mutable configuration builder. Create one with {@link Flylite#configure()}, call the
 * fluent methods and then {@link #load()} to get a {@link Flylite} instance.
 *
 * {@snippet :
 * Flylite flylite = Flylite.configure()
 * 	.dataSource("jdbc:postgresql://localhost/db", "user", "password")
 * 	.locations("classpath:db/migration")
 * 	.load();
 * flylite.migrate();
 * }
 */
public final class FluentConfiguration implements Configuration {

	private ClassLoader classLoader;

	private @Nullable String url;

	private @Nullable String user;

	private @Nullable String password;

	private @Nullable String driver;

	private @Nullable DataSource dataSource;

	private Map<String, String> jdbcProperties = Map.of();

	private int connectRetries = 0;

	private int connectRetriesInterval = 120;

	private @Nullable String initSql;

	private MigrationVersion baselineVersion = MigrationVersion.fromVersion("1");

	private String baselineDescription = "<< Flyway Baseline >>";

	private List<MigrationResolver> resolvers = List.of();

	private boolean skipDefaultResolvers = false;

	private List<Callback> callbacks = List.of();

	private boolean skipDefaultCallbacks = false;

	private String sqlMigrationPrefix = "V";

	private String repeatableSqlMigrationPrefix = "R";

	private String sqlMigrationSeparator = "__";

	private List<String> sqlMigrationSuffixes = List.of(".sql");

	private List<JavaMigration> javaMigrations = List.of();

	private boolean executeInTransaction = true;

	private boolean placeholderReplacement = true;

	private String placeholderPrefix = "${";

	private String placeholderSuffix = "}";

	private String placeholderSeparator = ":";

	private Map<String, String> placeholders = Map.of();

	private MigrationVersion target = MigrationVersion.LATEST;

	private String table = DEFAULT_TABLE;

	private @Nullable String tablespace;

	private @Nullable String defaultSchema;

	private List<String> schemas = List.of();

	private Charset encoding = java.nio.charset.StandardCharsets.UTF_8;

	private List<Location> locations = List.of(new Location("db/migration"));

	private boolean baselineOnMigrate = false;

	private boolean outOfOrder = false;

	private List<ValidatePattern> ignoreMigrationPatterns = List.of(ValidatePattern.fromPattern("*:future"));

	private boolean validateMigrationNaming = false;

	private boolean validateOnMigrate = true;

	private boolean cleanDisabled = true;

	private boolean mixed = false;

	private boolean group = false;

	private @Nullable String installedBy;

	private @Nullable ResourceProvider resourceProvider;

	private @Nullable ClassProvider<JavaMigration> javaMigrationClassProvider;

	private boolean createSchemas = true;

	private int lockRetryCount = 50;

	private boolean failOnMissingLocations = false;

	private boolean postgresqlTransactionalLock = true;

	/**
	 * Creates a configuration with defaults using the thread context class loader (or
	 * this class's loader if there is none).
	 */
	public FluentConfiguration() {
		this(null);
	}

	/**
	 * Creates a configuration with defaults.
	 * @param classLoader class loader used to load migrations, resources and drivers. If
	 * {@code null} the thread context class loader is used.
	 */
	public FluentConfiguration(@Nullable ClassLoader classLoader) {
		this.classLoader = resolveClassLoader(classLoader);
	}

	private static ClassLoader resolveClassLoader(@Nullable ClassLoader classLoader) {
		if (classLoader != null) {
			return classLoader;
		}
		var context = Thread.currentThread().getContextClassLoader();
		if (context != null) {
			return context;
		}
		return Objects.requireNonNullElse(FluentConfiguration.class.getClassLoader(),
				ClassLoader.getSystemClassLoader());
	}

	/**
	 * Loads this configuration into a new {@link Flylite} instance. Further changes to
	 * this configuration do not affect the returned instance.
	 * @return flylite.
	 */
	public Flylite load() {
		return new Flylite(new FluentConfiguration(classLoader).configuration(this));
	}

	/**
	 * Copies all settings of the given configuration into this one.
	 * @param configuration to copy.
	 * @return this.
	 */
	public FluentConfiguration configuration(Configuration configuration) {
		this.classLoader = configuration.getClassLoader();
		this.url = configuration.getUrl();
		this.user = configuration.getUser();
		this.password = configuration.getPassword();
		this.driver = configuration.getDriver();
		this.dataSource = configuration.getDataSource();
		this.jdbcProperties = Map.copyOf(configuration.getJdbcProperties());
		this.connectRetries = configuration.getConnectRetries();
		this.connectRetriesInterval = configuration.getConnectRetriesInterval();
		this.initSql = configuration.getInitSql();
		this.baselineVersion = configuration.getBaselineVersion();
		this.baselineDescription = configuration.getBaselineDescription();
		this.resolvers = List.copyOf(configuration.getResolvers());
		this.skipDefaultResolvers = configuration.isSkipDefaultResolvers();
		this.callbacks = List.copyOf(configuration.getCallbacks());
		this.skipDefaultCallbacks = configuration.isSkipDefaultCallbacks();
		this.sqlMigrationPrefix = configuration.getSqlMigrationPrefix();
		this.repeatableSqlMigrationPrefix = configuration.getRepeatableSqlMigrationPrefix();
		this.sqlMigrationSeparator = configuration.getSqlMigrationSeparator();
		this.sqlMigrationSuffixes = List.copyOf(configuration.getSqlMigrationSuffixes());
		this.javaMigrations = List.copyOf(configuration.getJavaMigrations());
		this.executeInTransaction = configuration.isExecuteInTransaction();
		this.placeholderReplacement = configuration.isPlaceholderReplacement();
		this.placeholderPrefix = configuration.getPlaceholderPrefix();
		this.placeholderSuffix = configuration.getPlaceholderSuffix();
		this.placeholderSeparator = configuration.getPlaceholderSeparator();
		this.placeholders = copyMap(configuration.getPlaceholders());
		this.target = configuration.getTarget();
		this.table = configuration.getTable();
		this.tablespace = configuration.getTablespace();
		this.defaultSchema = configuration.getDefaultSchema();
		this.schemas = List.copyOf(configuration.getSchemas());
		this.encoding = configuration.getEncoding();
		this.locations = List.copyOf(configuration.getLocations());
		this.baselineOnMigrate = configuration.isBaselineOnMigrate();
		this.outOfOrder = configuration.isOutOfOrder();
		this.ignoreMigrationPatterns = List.copyOf(configuration.getIgnoreMigrationPatterns());
		this.validateMigrationNaming = configuration.isValidateMigrationNaming();
		this.validateOnMigrate = configuration.isValidateOnMigrate();
		this.cleanDisabled = configuration.isCleanDisabled();
		this.mixed = configuration.isMixed();
		this.group = configuration.isGroup();
		this.installedBy = configuration.getInstalledBy();
		this.resourceProvider = configuration.getResourceProvider();
		this.javaMigrationClassProvider = configuration.getJavaMigrationClassProvider();
		this.createSchemas = configuration.isCreateSchemas();
		this.lockRetryCount = configuration.getLockRetryCount();
		this.failOnMissingLocations = configuration.isFailOnMissingLocations();
		this.postgresqlTransactionalLock = configuration.isPostgresqlTransactionalLock();
		return this;
	}

	private static Map<String, String> copyMap(Map<String, String> map) {
		return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(map));
	}

	@Override
	public ClassLoader getClassLoader() {
		return classLoader;
	}

	@Override
	public @Nullable String getUrl() {
		return url;
	}

	@Override
	public @Nullable String getUser() {
		return user;
	}

	@Override
	public @Nullable String getPassword() {
		return password;
	}

	@Override
	public @Nullable String getDriver() {
		return driver;
	}

	@Override
	public @Nullable DataSource getDataSource() {
		var ds = this.dataSource;
		if (ds == null) {
			var u = this.url;
			if (u != null && !u.isBlank()) {
				ds = new DriverDataSource(classLoader, driver, u, user, password, jdbcProperties);
				this.dataSource = ds;
			}
		}
		return ds;
	}

	@Override
	public Map<String, String> getJdbcProperties() {
		return jdbcProperties;
	}

	@Override
	public int getConnectRetries() {
		return connectRetries;
	}

	@Override
	public int getConnectRetriesInterval() {
		return connectRetriesInterval;
	}

	@Override
	public @Nullable String getInitSql() {
		return initSql;
	}

	@Override
	public MigrationVersion getBaselineVersion() {
		return baselineVersion;
	}

	@Override
	public String getBaselineDescription() {
		return baselineDescription;
	}

	@Override
	public List<MigrationResolver> getResolvers() {
		return resolvers;
	}

	@Override
	public boolean isSkipDefaultResolvers() {
		return skipDefaultResolvers;
	}

	@Override
	public List<Callback> getCallbacks() {
		return callbacks;
	}

	@Override
	public boolean isSkipDefaultCallbacks() {
		return skipDefaultCallbacks;
	}

	@Override
	public String getSqlMigrationPrefix() {
		return sqlMigrationPrefix;
	}

	@Override
	public String getRepeatableSqlMigrationPrefix() {
		return repeatableSqlMigrationPrefix;
	}

	@Override
	public String getSqlMigrationSeparator() {
		return sqlMigrationSeparator;
	}

	@Override
	public List<String> getSqlMigrationSuffixes() {
		return sqlMigrationSuffixes;
	}

	@Override
	public List<JavaMigration> getJavaMigrations() {
		return javaMigrations;
	}

	@Override
	public boolean isExecuteInTransaction() {
		return executeInTransaction;
	}

	@Override
	public boolean isPlaceholderReplacement() {
		return placeholderReplacement;
	}

	@Override
	public String getPlaceholderSuffix() {
		return placeholderSuffix;
	}

	@Override
	public String getPlaceholderPrefix() {
		return placeholderPrefix;
	}

	@Override
	public String getPlaceholderSeparator() {
		return placeholderSeparator;
	}

	@Override
	public Map<String, String> getPlaceholders() {
		return placeholders;
	}

	@Override
	public MigrationVersion getTarget() {
		return target;
	}

	@Override
	public String getTable() {
		return table;
	}

	@Override
	public @Nullable String getTablespace() {
		return tablespace;
	}

	@Override
	public @Nullable String getDefaultSchema() {
		return defaultSchema;
	}

	@Override
	public List<String> getSchemas() {
		return schemas;
	}

	@Override
	public Charset getEncoding() {
		return encoding;
	}

	@Override
	public List<Location> getLocations() {
		return locations;
	}

	@Override
	public boolean isBaselineOnMigrate() {
		return baselineOnMigrate;
	}

	@Override
	public boolean isOutOfOrder() {
		return outOfOrder;
	}

	@Override
	public List<ValidatePattern> getIgnoreMigrationPatterns() {
		return ignoreMigrationPatterns;
	}

	@Override
	public boolean isValidateMigrationNaming() {
		return validateMigrationNaming;
	}

	@Override
	public boolean isValidateOnMigrate() {
		return validateOnMigrate;
	}

	@Override
	public boolean isCleanDisabled() {
		return cleanDisabled;
	}

	@Override
	public boolean isMixed() {
		return mixed;
	}

	@Override
	public boolean isGroup() {
		return group;
	}

	@Override
	public @Nullable String getInstalledBy() {
		return installedBy;
	}

	@Override
	public @Nullable ResourceProvider getResourceProvider() {
		return resourceProvider;
	}

	@Override
	public @Nullable ClassProvider<JavaMigration> getJavaMigrationClassProvider() {
		return javaMigrationClassProvider;
	}

	@Override
	public boolean isCreateSchemas() {
		return createSchemas;
	}

	@Override
	public int getLockRetryCount() {
		return lockRetryCount;
	}

	@Override
	public boolean isFailOnMissingLocations() {
		return failOnMissingLocations;
	}

	@Override
	public boolean isPostgresqlTransactionalLock() {
		return postgresqlTransactionalLock;
	}

	/**
	 * Sets the datasource to migrate.
	 * @param dataSource datasource.
	 * @return this.
	 */
	public FluentConfiguration dataSource(DataSource dataSource) {
		this.dataSource = Objects.requireNonNull(dataSource);
		this.url = null;
		this.user = null;
		this.password = null;
		return this;
	}

	/**
	 * Sets the JDBC url and credentials used to create a datasource. The driver is
	 * detected from the url unless {@link #driver(String)} is set.
	 * @param url jdbc url.
	 * @param user user or {@code null}.
	 * @param password password or {@code null}.
	 * @return this.
	 */
	public FluentConfiguration dataSource(String url, @Nullable String user, @Nullable String password) {
		this.url = Objects.requireNonNull(url);
		this.user = user;
		this.password = password;
		this.dataSource = null;
		return this;
	}

	/**
	 * Sets the JDBC driver class name. Only used with
	 * {@link #dataSource(String, String, String)}.
	 * @param driver driver class name or {@code null} to detect from the url.
	 * @return this.
	 */
	public FluentConfiguration driver(@Nullable String driver) {
		this.driver = driver;
		this.dataSource = this.url == null ? this.dataSource : null;
		return this;
	}

	/**
	 * Sets extra JDBC driver properties. Only used with
	 * {@link #dataSource(String, String, String)}.
	 * @param jdbcProperties properties.
	 * @return this.
	 */
	public FluentConfiguration jdbcProperties(Map<String, String> jdbcProperties) {
		this.jdbcProperties = copyMap(jdbcProperties);
		this.dataSource = this.url == null ? this.dataSource : null;
		return this;
	}

	/**
	 * Sets the class loader used to load migrations, resources and drivers.
	 * @param classLoader class loader.
	 * @return this.
	 */
	public FluentConfiguration classLoader(ClassLoader classLoader) {
		this.classLoader = Objects.requireNonNull(classLoader);
		return this;
	}

	/**
	 * Sets the maximum number of connection retries.
	 * @param connectRetries retries, must not be negative.
	 * @return this.
	 */
	public FluentConfiguration connectRetries(int connectRetries) {
		if (connectRetries < 0) {
			throw new FlyliteException("Invalid number of connectRetries (must be 0 or greater): " + connectRetries,
					ErrorCode.CONFIGURATION);
		}
		this.connectRetries = connectRetries;
		return this;
	}

	/**
	 * Sets the maximum time between connection retries in seconds.
	 * @param connectRetriesInterval seconds, must not be negative.
	 * @return this.
	 */
	public FluentConfiguration connectRetriesInterval(int connectRetriesInterval) {
		if (connectRetriesInterval < 0) {
			throw new FlyliteException(
					"Invalid number for connectRetriesInterval (must be 0 or greater): " + connectRetriesInterval,
					ErrorCode.CONFIGURATION);
		}
		this.connectRetriesInterval = connectRetriesInterval;
		return this;
	}

	/**
	 * Sets SQL to run when a connection is opened.
	 * @param initSql sql or {@code null}.
	 * @return this.
	 */
	public FluentConfiguration initSql(@Nullable String initSql) {
		this.initSql = initSql;
		return this;
	}

	/**
	 * Sets the baseline version.
	 * @param baselineVersion version.
	 * @return this.
	 */
	public FluentConfiguration baselineVersion(MigrationVersion baselineVersion) {
		this.baselineVersion = Objects.requireNonNull(baselineVersion);
		return this;
	}

	/**
	 * Sets the baseline version.
	 * @param baselineVersion version string like {@code 1.1}.
	 * @return this.
	 */
	public FluentConfiguration baselineVersion(String baselineVersion) {
		return baselineVersion(MigrationVersion.fromVersion(baselineVersion));
	}

	/**
	 * Sets the baseline description.
	 * @param baselineDescription description.
	 * @return this.
	 */
	public FluentConfiguration baselineDescription(String baselineDescription) {
		this.baselineDescription = Objects.requireNonNull(baselineDescription);
		return this;
	}

	/**
	 * Sets custom migration resolvers.
	 * @param resolvers resolvers.
	 * @return this.
	 */
	public FluentConfiguration resolvers(MigrationResolver... resolvers) {
		this.resolvers = List.of(resolvers);
		return this;
	}

	/**
	 * Whether to skip the built-in resolvers.
	 * @param skipDefaultResolvers skip.
	 * @return this.
	 */
	public FluentConfiguration skipDefaultResolvers(boolean skipDefaultResolvers) {
		this.skipDefaultResolvers = skipDefaultResolvers;
		return this;
	}

	/**
	 * Sets lifecycle callbacks.
	 * @param callbacks callbacks.
	 * @return this.
	 */
	public FluentConfiguration callbacks(Callback... callbacks) {
		this.callbacks = List.of(callbacks);
		return this;
	}

	/**
	 * Whether to skip SQL callbacks found in locations.
	 * @param skipDefaultCallbacks skip.
	 * @return this.
	 */
	public FluentConfiguration skipDefaultCallbacks(boolean skipDefaultCallbacks) {
		this.skipDefaultCallbacks = skipDefaultCallbacks;
		return this;
	}

	/**
	 * Sets the versioned SQL migration prefix.
	 * @param sqlMigrationPrefix prefix.
	 * @return this.
	 */
	public FluentConfiguration sqlMigrationPrefix(String sqlMigrationPrefix) {
		this.sqlMigrationPrefix = Objects.requireNonNull(sqlMigrationPrefix);
		return this;
	}

	/**
	 * Sets the repeatable SQL migration prefix.
	 * @param repeatableSqlMigrationPrefix prefix.
	 * @return this.
	 */
	public FluentConfiguration repeatableSqlMigrationPrefix(String repeatableSqlMigrationPrefix) {
		this.repeatableSqlMigrationPrefix = Objects.requireNonNull(repeatableSqlMigrationPrefix);
		return this;
	}

	/**
	 * Sets the SQL migration separator.
	 * @param sqlMigrationSeparator separator, must not be empty.
	 * @return this.
	 */
	public FluentConfiguration sqlMigrationSeparator(String sqlMigrationSeparator) {
		if (sqlMigrationSeparator.isEmpty()) {
			throw new FlyliteException("sqlMigrationSeparator cannot be empty!", ErrorCode.CONFIGURATION);
		}
		this.sqlMigrationSeparator = sqlMigrationSeparator;
		return this;
	}

	/**
	 * Sets the SQL migration suffixes.
	 * @param sqlMigrationSuffixes suffixes.
	 * @return this.
	 */
	public FluentConfiguration sqlMigrationSuffixes(String... sqlMigrationSuffixes) {
		this.sqlMigrationSuffixes = List.of(sqlMigrationSuffixes);
		return this;
	}

	/**
	 * Sets Java migration instances to use in addition to scanned ones.
	 * @param javaMigrations migrations.
	 * @return this.
	 */
	public FluentConfiguration javaMigrations(JavaMigration... javaMigrations) {
		this.javaMigrations = List.of(javaMigrations);
		return this;
	}

	/**
	 * Whether SQL migrations run within a transaction.
	 * @param executeInTransaction use transactions.
	 * @return this.
	 */
	public FluentConfiguration executeInTransaction(boolean executeInTransaction) {
		this.executeInTransaction = executeInTransaction;
		return this;
	}

	/**
	 * Whether placeholders are replaced.
	 * @param placeholderReplacement replace.
	 * @return this.
	 */
	public FluentConfiguration placeholderReplacement(boolean placeholderReplacement) {
		this.placeholderReplacement = placeholderReplacement;
		return this;
	}

	/**
	 * Sets the placeholders replaced in SQL migrations.
	 * @param placeholders placeholders.
	 * @return this.
	 */
	public FluentConfiguration placeholders(Map<String, String> placeholders) {
		this.placeholders = copyMap(placeholders);
		return this;
	}

	/**
	 * Sets the placeholder prefix.
	 * @param placeholderPrefix prefix, must not be empty.
	 * @return this.
	 */
	public FluentConfiguration placeholderPrefix(String placeholderPrefix) {
		if (placeholderPrefix.isEmpty()) {
			throw new FlyliteException("placeholderPrefix cannot be empty!", ErrorCode.CONFIGURATION);
		}
		this.placeholderPrefix = placeholderPrefix;
		return this;
	}

	/**
	 * Sets the placeholder suffix.
	 * @param placeholderSuffix suffix, must not be empty.
	 * @return this.
	 */
	public FluentConfiguration placeholderSuffix(String placeholderSuffix) {
		if (placeholderSuffix.isEmpty()) {
			throw new FlyliteException("placeholderSuffix cannot be empty!", ErrorCode.CONFIGURATION);
		}
		this.placeholderSuffix = placeholderSuffix;
		return this;
	}

	/**
	 * Sets the placeholder separator.
	 * @param placeholderSeparator separator, must not be empty.
	 * @return this.
	 */
	public FluentConfiguration placeholderSeparator(String placeholderSeparator) {
		if (placeholderSeparator.isEmpty()) {
			throw new FlyliteException("placeholderSeparator cannot be empty!", ErrorCode.CONFIGURATION);
		}
		this.placeholderSeparator = placeholderSeparator;
		return this;
	}

	/**
	 * Sets the target version.
	 * @param target version.
	 * @return this.
	 */
	public FluentConfiguration target(MigrationVersion target) {
		this.target = Objects.requireNonNull(target);
		return this;
	}

	/**
	 * Sets the target version.
	 * @param target {@code latest}, {@code current} or a version like {@code 1.2}.
	 * @return this.
	 */
	public FluentConfiguration target(String target) {
		return target(MigrationVersion.fromVersion(target));
	}

	/**
	 * Sets the schema history table name.
	 * @param table table name.
	 * @return this.
	 */
	public FluentConfiguration table(String table) {
		this.table = Objects.requireNonNull(table);
		return this;
	}

	/**
	 * Sets the tablespace of the schema history table.
	 * @param tablespace tablespace or {@code null}.
	 * @return this.
	 */
	public FluentConfiguration tablespace(@Nullable String tablespace) {
		this.tablespace = tablespace;
		return this;
	}

	/**
	 * Sets the default schema.
	 * @param schema schema or {@code null}.
	 * @return this.
	 */
	public FluentConfiguration defaultSchema(@Nullable String schema) {
		this.defaultSchema = schema;
		return this;
	}

	/**
	 * Sets the schemas managed by Flylite.
	 * @param schemas schemas.
	 * @return this.
	 */
	public FluentConfiguration schemas(String... schemas) {
		this.schemas = List.of(schemas);
		return this;
	}

	/**
	 * Sets the encoding of SQL migrations.
	 * @param encoding charset.
	 * @return this.
	 */
	public FluentConfiguration encoding(Charset encoding) {
		this.encoding = Objects.requireNonNull(encoding);
		return this;
	}

	/**
	 * Sets the encoding of SQL migrations.
	 * @param encoding charset name.
	 * @return this.
	 */
	public FluentConfiguration encoding(String encoding) {
		return encoding(Charset.forName(encoding));
	}

	/**
	 * Sets the locations to scan. Prefix with {@code classpath:} or {@code filesystem:};
	 * no prefix means classpath.
	 * @param locations locations.
	 * @return this.
	 */
	public FluentConfiguration locations(String... locations) {
		List<Location> ls = new ArrayList<>(locations.length);
		for (String l : locations) {
			ls.add(new Location(l));
		}
		this.locations = List.copyOf(ls);
		return this;
	}

	/**
	 * Sets the locations to scan.
	 * @param locations locations.
	 * @return this.
	 */
	public FluentConfiguration locations(Location... locations) {
		this.locations = List.of(locations);
		return this;
	}

	/**
	 * Whether to baseline a non-empty schema without a schema history table on migrate.
	 * @param baselineOnMigrate baseline on migrate.
	 * @return this.
	 */
	public FluentConfiguration baselineOnMigrate(boolean baselineOnMigrate) {
		this.baselineOnMigrate = baselineOnMigrate;
		return this;
	}

	/**
	 * Whether migrations may be applied out of order.
	 * @param outOfOrder out of order.
	 * @return this.
	 */
	public FluentConfiguration outOfOrder(boolean outOfOrder) {
		this.outOfOrder = outOfOrder;
		return this;
	}

	/**
	 * Sets the patterns of migration states ignored by validate. Patterns are of the form
	 * {@code type:state} for example {@code *:missing} or {@code versioned:future}.
	 * @param ignoreMigrationPatterns patterns.
	 * @return this.
	 */
	public FluentConfiguration ignoreMigrationPatterns(String... ignoreMigrationPatterns) {
		List<ValidatePattern> ps = new ArrayList<>(ignoreMigrationPatterns.length);
		for (String p : ignoreMigrationPatterns) {
			if (!p.isBlank()) {
				ps.add(ValidatePattern.fromPattern(p));
			}
		}
		this.ignoreMigrationPatterns = List.copyOf(ps);
		return this;
	}

	/**
	 * Sets the patterns of migration states ignored by validate.
	 * @param ignoreMigrationPatterns patterns.
	 * @return this.
	 */
	public FluentConfiguration ignoreMigrationPatterns(ValidatePattern... ignoreMigrationPatterns) {
		this.ignoreMigrationPatterns = List.of(ignoreMigrationPatterns);
		return this;
	}

	/**
	 * Whether to fail on files in locations that do not follow the naming convention.
	 * @param validateMigrationNaming validate.
	 * @return this.
	 */
	public FluentConfiguration validateMigrationNaming(boolean validateMigrationNaming) {
		this.validateMigrationNaming = validateMigrationNaming;
		return this;
	}

	/**
	 * Whether migrate validates first.
	 * @param validateOnMigrate validate.
	 * @return this.
	 */
	public FluentConfiguration validateOnMigrate(boolean validateOnMigrate) {
		this.validateOnMigrate = validateOnMigrate;
		return this;
	}

	/**
	 * Whether clean is disabled. Clean drops everything in the configured schemas.
	 * @param cleanDisabled disabled.
	 * @return this.
	 */
	public FluentConfiguration cleanDisabled(boolean cleanDisabled) {
		this.cleanDisabled = cleanDisabled;
		return this;
	}

	/**
	 * Whether transactional and non-transactional statements may be mixed in a migration.
	 * @param mixed mixed.
	 * @return this.
	 */
	public FluentConfiguration mixed(boolean mixed) {
		this.mixed = mixed;
		return this;
	}

	/**
	 * Whether all pending migrations run in one transaction.
	 * @param group group.
	 * @return this.
	 */
	public FluentConfiguration group(boolean group) {
		this.group = group;
		return this;
	}

	/**
	 * Sets the user recorded as having applied migrations.
	 * @param installedBy user or {@code null} (or empty) for the database user.
	 * @return this.
	 */
	public FluentConfiguration installedBy(@Nullable String installedBy) {
		this.installedBy = installedBy == null || installedBy.isEmpty() ? null : installedBy;
		return this;
	}

	/**
	 * Sets a custom resource provider used instead of scanning locations.
	 * @param resourceProvider provider or {@code null}.
	 * @return this.
	 */
	public FluentConfiguration resourceProvider(@Nullable ResourceProvider resourceProvider) {
		this.resourceProvider = resourceProvider;
		return this;
	}

	/**
	 * Sets a custom Java migration class provider used instead of scanning locations.
	 * @param javaMigrationClassProvider provider or {@code null}.
	 * @return this.
	 */
	public FluentConfiguration javaMigrationClassProvider(
			@Nullable ClassProvider<JavaMigration> javaMigrationClassProvider) {
		this.javaMigrationClassProvider = javaMigrationClassProvider;
		return this;
	}

	/**
	 * Whether missing schemas are created.
	 * @param createSchemas create.
	 * @return this.
	 */
	public FluentConfiguration createSchemas(boolean createSchemas) {
		this.createSchemas = createSchemas;
		return this;
	}

	/**
	 * Sets the maximum number of lock retries.
	 * @param lockRetryCount retries, {@code -1} for infinite.
	 * @return this.
	 */
	public FluentConfiguration lockRetryCount(int lockRetryCount) {
		this.lockRetryCount = lockRetryCount;
		return this;
	}

	/**
	 * Whether missing locations fail.
	 * @param failOnMissingLocations fail.
	 * @return this.
	 */
	public FluentConfiguration failOnMissingLocations(boolean failOnMissingLocations) {
		this.failOnMissingLocations = failOnMissingLocations;
		return this;
	}

	/**
	 * Whether PostgreSQL uses a transaction scoped advisory lock.
	 * @param postgresqlTransactionalLock transactional lock.
	 * @return this.
	 */
	public FluentConfiguration postgresqlTransactionalLock(boolean postgresqlTransactionalLock) {
		this.postgresqlTransactionalLock = postgresqlTransactionalLock;
		return this;
	}

}
