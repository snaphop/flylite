# AGENTS.md

Guidance for AI coding agents (and new contributors) working on Flylite.

## What this is

Flylite is a rewrite of Flyway 9.19.1 (the `snaphop` branch of the fork in
`../flyway`) that supports **only PostgreSQL and SQLite** and **only the Java API and
the Maven plugin**. Keep it that way: do not add databases, a CLI, config files or
plugin/extension mechanisms. When in doubt, delete code rather than add it.

**Compatibility with Flyway is a hard requirement.** The schema history table
(`flyway_schema_history`, its columns, `type` values such as `SQL`/`JDBC`/`BASELINE`/
`SCHEMA`/`DELETE`, the `<< Flyway Schema Creation >>` / `<< Flyway Baseline >>`
markers), the checksum algorithm, the `${flyway:...}` placeholders and the PostgreSQL
advisory lock id must stay identical. `FlywayCompatibilityTest` runs real Flyway 9.19.1
against the same databases; it must keep passing.

## Layout

| Module | Purpose |
| --- | --- |
| `flylite` | The library. JPMS module `com.snaphop.flylite`. Public API in `com.snaphop.flylite` and `com.snaphop.flylite.api.*`; everything else is in `com.snaphop.flylite.internal.*` and is not exported. |
| `flylite-maven-plugin` | Maven plugin (no `module-info`, Maven loads plugins on the classpath). Integration tests in `src/it` run real Maven builds with the invoker. |
| `flylite-test` | End to end tests (not published). Runs on the classpath against SQLite and PostgreSQL (Testcontainers). |
| `flylite-coverage-aggregate` | Only holds the JaCoCo `report-aggregate` (not published). |

`flylite-test` has two test packages:

* `com.snaphop.flylite.test` - Flylite's own end to end tests.
* `com.snaphop.flylite.upstream` - tests (and resources under `upstream/`) ported from
  Flyway 4.2's removed test suite. Keep them separate; mark anything new there as ported.

## Building

Always use the wrapper. Docker must be running for the PostgreSQL tests.

```bash
./mvnw clean verify                 # everything: unit, e2e, plugin ITs
./mvnw clean verify -Dcodecover     # + coverage: flylite-coverage-aggregate/target/site/jacoco-aggregate
./mvnw verify -pl flylite-test -Dtest=MigrateTest   # one test class (install flylite first)
./mvnw verify -Dinvoker.skip        # skip the plugin integration tests
```

Java 21 is the release target (`maven.compiler.release` via `java.version`); the build
runs on newer JDKs. The compiler runs with `-Xlint:all -Werror`; fix warnings, don't
suppress them without a comment.

`flylite.test.postgres.image` (system property) selects the PostgreSQL image
(default `postgres:17-alpine`).

## Tests: prefer end to end

Coverage is measured from the end to end tests, not unit tests, so that code that is
never exercised through the public API shows up as uncovered and can be deleted.

* Add tests to `flylite-test` that go through `Flylite.configure()...load()` against
  both databases (`@ParameterizedTest @EnumSource(Db.class)`). `Db.create(dir)` returns a
  fresh, empty database for each call. Use `Migrations` to write migration scripts to a
  temp directory when a test needs to change them between runs.
* Maven plugin behaviour belongs in an invoker project under
  `flylite-maven-plugin/src/it` with a `verify.groovy`.
* The only test inside `flylite` itself is `ModulePathTest`, which checks the module
  descriptor by running as a named module.
* Before adding code, check the aggregate coverage report: uncovered code is a candidate
  for deletion, not for a unit test.

## Formatting

Spring Java Format (tabs) is validated during the normal build, so unformatted code fails
the build. Apply it with:

```bash
./mvnw process-sources -Dformat.apply -pl <modules>
```

## Null safety

Every package is `@org.jspecify.annotations.NullMarked` (see `package-info.java`); use
`@Nullable` for anything that can be null. Do not use `Optional` except as a stream
result. Run the analysis before finishing:

```bash
bin/analyze.sh                      # nullaway, checkerframework and errorprone
bin/analyze.sh nullaway             # one profile
```

Checker Framework stub files live in `etc/checker`.

## Conventions

* Files derived from Flyway keep the Red Gate Apache 2.0 header plus
  `Modifications Copyright (C) Snaphop 2026`. New files use the same header.
* Logging uses `com.snaphop.flylite.internal.logging.Log` (a thin wrapper over
  `System.Logger`). No logging dependencies.
* No Lombok. Prefer records, sealed types, `switch` expressions and `List`/`Map` over
  arrays in new code.

## Releasing

Artifacts are deployed to Snaphop's Maven repository (`maven.snaphop.com`) only, not Maven Central.
`.snaphop/settings.xml` defines the repositories used by `distributionManagement`; the
password comes from `NEXUS_SNAPHOP_COM_PASSWORD` (a GitHub secret in CI).

* Releases are `X.Y.Z`, starting at `1.0.0`; after each release the patch is bumped
  (`1.0.1-SNAPSHOT`, ...).
* `bin/release.sh` releases the current snapshot: sets the version, runs the build, commits,
  tags `vX.Y.Z`, bumps to the next patch snapshot and pushes. The tag push runs
  `.github/workflows/deploy-snaphop.yml`, which deploys the release.
* Snapshots: run the "Deploy to SnapHop" workflow manually on a branch.
* `bin/deploy.sh` deploys from a local machine if the workflow cannot be used.
* Only release when the user asks for it.
