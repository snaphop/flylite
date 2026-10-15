# Flylite

Flylite is a small rewrite of [Flyway](https://github.com/flyway/flyway) (forked from the
snaphop branch of Flyway 9.19.1) that only does what we need:

* **Databases:** PostgreSQL (10+) and SQLite. Nothing else.
* **Entry points:** the Java API and a Maven plugin. No command line, Gradle, TOML, config
  files or environment variable configuration.
* **Modern Java:** Java 21, JPMS module `com.snaphop.flylite`, no Lombok, JSpecify null
  annotations (checked with NullAway and the Checker Framework), records and sealed types.
* **No runtime dependencies:** logging goes through `System.Logger`.

It is a drop-in replacement at the database level: the schema history table
(`flyway_schema_history`), its columns, migration types and checksums are the same as
Flyway's, so databases migrated with Flyway can be migrated with Flylite and vice versa.

## Java

```java
Flylite flylite = Flylite.configure()
    .dataSource(dataSource) // or .dataSource(url, user, password)
    .locations("classpath:db/migration")
    .baselineOnMigrate(true)
    .load();
flylite.migrate();
```

The API mirrors Flyway's `FluentConfiguration`: replace `org.flywaydb.core` with
`com.snaphop.flylite` and `Flyway` with `Flylite`.

Commands: `migrate`, `validate`/`validateWithResult`, `info`, `baseline`, `repair` and
`clean` (disabled by default).

Migrations: versioned (`V1__name.sql`), repeatable (`R__name.sql`), Java migrations
(`BaseJavaMigration`/`JavaMigration`), SQL callbacks (`beforeMigrate.sql`, ...), Java
callbacks, placeholders (`${name}`, `${flyway:defaultSchema}`, ...) and per script
`.conf` files (`executeInTransaction`, `encoding`, `placeholderReplacement`).

### Modules

If migrations are classpath resources inside a named module the package containing them
must be opened, for example `opens db.migration;`.

## Maven

```xml
<plugin>
  <groupId>com.snaphop.flylite</groupId>
  <artifactId>flylite-maven-plugin</artifactId>
  <version>${flylite.version}</version>
  <configuration>
    <url>${db.url}</url>
    <user>${db.user}</user>
    <password>${db.password}</password>
    <locations>
      <location>filesystem:src/main/resources/db/migration</location>
    </locations>
  </configuration>
  <dependencies>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <version>${postgresql.version}</version>
    </dependency>
  </dependencies>
</plugin>
```

Goals: `flylite:migrate`, `flylite:validate`, `flylite:info`, `flylite:baseline`,
`flylite:repair`, `flylite:clean`. Every parameter can also be set with a `flylite.`
prefixed property, e.g. `-Dflylite.skip=true`.

## What was removed compared to Flyway

Every database other than PostgreSQL and SQLite, the command line, Gradle plugin, config
files/TOML/environment configuration, Redgate licensing/telemetry/Teams features (undo,
dry run, cherry pick, batch, stream, error overrides, ...), cloud (S3/GCS) locations,
JBoss VFS/OSGi scanning, pluggable loggers and the HTML/JSON reports.

## License

Apache License 2.0. See [NOTICE](NOTICE).
