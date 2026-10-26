# Plan: Java, Hibernate and SQL types for time values

- **Issue**: #228 (Decide the Java type to Hibernate/SQL mapping for each time type (Instant vs LocalDateTime))
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The persistence convention says, once, which Java type and which PostgreSQL type hold each kind of
time value (a moment, a calendar date, a time of day, a duration), with the time zone rules, and the
build fails when an entity, a migration or production code uses a type the convention rules out
(`LocalDateTime`, `TIMESTAMP` without time zone, `OffsetDateTime`, `TIMETZ`, `INTERVAL`,
`java.util.Date`, ...). The existing entities and migrations already follow the decision, so no
migration and no entity changes: the pull request adds the rule, the two checks and their proofs.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend, `:backend:library:persistence` test fixtures and tests | [backend-java-persistence.md](../docs/coding-convention/backend-java-persistence.md) "Shared persistence code" and "Tests": a catalog check is a package-private class in the test fixtures, reads the catalog with plain JDBC through `CatalogQueries`, is run by `DomainPersistenceConventionsTest`, and is proved by a library test against a fixture migration of a made-up schema (as `DatabaseNamingCheck` / `DatabaseNamingCheckTest` / `fixture/naming/`). [backend-database-naming.md](../docs/coding-convention/backend-database-naming.md) for every name in the fixture migrations |
| Backend, `artifact/backend` ArchUnit tests | `PersistenceArchitectureTest` holds the persistence rules; rule fields are snake_case sentences with a `because(...)` naming the document ([backend-java-checkstyle.md](../docs/coding-convention/backend-java-checkstyle.md) suppressions already cover the class) |
| Docs | [docs/coding-convention/README.md](../docs/coding-convention/README.md) "Changing a convention": the document, its check and the code change in one pull request; [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md) |

## Design

### The decision (written into backend-java-persistence.md)

| Kind of value | Domain core | Entity field | Column | Today |
|---|---|---|---|---|
| A moment in time: something happened or ends at a point on the time line | `Instant` | `Instant` | `TIMESTAMPTZ` | `ANALYSES.CREATED_AT`, `STARTED_AT`, `ENDED_AT`, `VISIBILITY_CHANGED_AT`; `USERS.CREATED_AT`, `UPDATED_AT`, `LAST_LOGIN_AT`, `LOCKED_UNTIL`; `PRESETS.UPDATED_AT` |
| A calendar date with no time and no zone (a market's trading day) | `LocalDate` | `LocalDate` | `DATE` | `ANALYSES.TRADE_DATE` |
| A time of day with no date | `LocalTime` | `LocalTime` | `TIME` (without time zone); a zone it belongs to is a separate `TEXT` column holding a zone ID | not used |
| A duration | `Duration` | `long` in the column's unit, converted by the MapStruct mapper | `BIGINT`, the column named with its unit (`_MS`) | `ANALYSES.ELAPSED_MS` (`RunStats.elapsed` ↔ `RunStatsEmbeddable.elapsedMs`) |

Ruled out, each with its reason in the document:

- `LocalDateTime` and `TIMESTAMP` (without time zone): a wall-clock reading without a zone; the
  same value is a different moment depending on the zone of whoever wrote it.
- `OffsetDateTime`, `ZonedDateTime`: PostgreSQL keeps no offset in a `TIMESTAMPTZ`, so Hibernate
  normalises the value to UTC and the offset is lost on the way back; the type promises what the
  column does not keep. `Instant` says what is stored.
- `OffsetTime` and `TIMETZ`: an offset without a date cannot follow daylight saving time
  (PostgreSQL's own documentation advises against `TIMETZ`).
- `java.util.Date`, `java.util.Calendar`, `java.sql.Timestamp` / `Date` / `Time`: the legacy API,
  mutable, with the JVM's default zone in their conversions.
- `INTERVAL` and a `Duration` entity field: Hibernate's mapping of `Duration` depends on its
  settings, and the runner reports durations as numbers already; a `BIGINT` with the unit in the name
  reads the same in `psql`, in the entity and in a migration.

Time zone rules (same section):

- Every stored moment is UTC. `TIMESTAMPTZ` stores UTC whatever the session's zone, and Hibernate
  binds an `Instant` as a UTC timestamp, so neither the JVM's default zone nor the database's
  `TimeZone` setting changes a stored value. No `hibernate.jdbc.time_zone` is set, and none is
  needed.
- A date has no zone. A date derived from "now" takes the zone of the one `Clock` bean
  (`Clock.systemUTC()`): `LocalDate.now(clock)`, so "today" changes at midnight UTC
  (`AnalysisService` validates the trade date that way).
- In the REST API an `Instant` is ISO-8601 in UTC with `Z` (OpenAPI `format: date-time`) and a
  `LocalDate` is `YYYY-MM-DD` (`format: date`); the UI shows a moment in the viewer's zone and a date
  as it is (`useLabels`, `PriceChart`).
- Precision: `TIMESTAMPTZ` keeps microseconds and rounds finer values, so a value that is compared
  after it was read back is cut to microseconds before it is saved (`ClockDateTimeProvider`), or to
  the precision of its source (`Analysis#millis` for imported runs).

Column name suffixes, as guidance in backend-database-naming.md (not checked): a moment ends in
`_AT`, or `_UNTIL` for the end of a period (`LOCKED_UNTIL`); a date in `_DATE`; a duration in its
unit (`_MS`).

The existing code was checked against this: every `TIMESTAMPTZ` column above maps to an `Instant`,
`TRADE_DATE` to a `LocalDate`, `ELAPSED_MS` to a `long` with `Duration` in the core; production code
uses no other time type (`LocalDateTime` nowhere, `ZoneOffset` / `ZoneId` / `java.util.Date` in tests
and test fixtures only). No migration and no entity changes.

### Check 1: column types in the catalog (`ColumnTypeCheck`)

A new package-private `ColumnTypeCheck` in the test fixtures of `:backend:library:persistence`, next
to `DatabaseNamingCheck` and built like it: `static List<String> violations(DataSource, String
schema)` reads `information_schema.columns` joined to the base tables of the schema through
`CatalogQueries.query`, and reports every column whose `udt_name` is `timestamp`, `timetz` or
`interval`, or an array of one (`_timestamp`, `_timetz`, `_interval`). `FLYWAY_SCHEMA_HISTORY` is
exempt: Flyway creates its `installed_on` as `timestamp` and it cannot be configured (the same
exemption as in the naming check). One sorted line per violation, in the naming check's style:

```
column "DUE" of table "SAMPLE_TYPES"."TYPES_WRONG": timestamp without time zone, use TIMESTAMPTZ (Instant)
column "OPENS" of table "SAMPLE_TYPES"."TYPES_WRONG": time with time zone, use TIME (LocalTime)
column "TOOK" of table "SAMPLE_TYPES"."TYPES_WRONG": interval, use BIGINT with the unit in the name (Duration)
```

`DomainPersistenceConventionsTest` gets a fifth test, `columnTypesFollowTheConvention`: migrate with
the domain's Flyway bean on a fresh database (as `namesFollowTheConvention`), then assert
`ColumnTypeCheck.violations(dataSource, schema)` is empty, with the message "column types that break
backend-java-persistence.md". The three domains' conventions tests pick it up without a change.

### Check 2: Java types (ArchUnit, `PersistenceArchitectureTest`)

Two rules in a "Time types" group:

- `production_code_uses_only_the_time_types_of_the_convention`: no class in `ROOT` depends on
  `LocalDateTime`, `OffsetDateTime`, `ZonedDateTime`, `OffsetTime`, `java.util.Date`,
  `java.util.Calendar` (subclasses included) or `java.util.TimeZone`. `java.sql` is already banned by
  `production_code_does_not_use_plain_sql`. The importer already leaves out tests and test fixtures,
  so the tests' `java.util.Date` (Spring Session) and fixed clocks stay allowed.
- `entities_map_time_to_the_convention_types`: every field of an `@Entity`, `@Embeddable` or
  `@MappedSuperclass` whose raw type is in package `java.time` is an `Instant`, a `LocalDate` or a
  `LocalTime`. This is what rules out a `Duration`, `Year`, `YearMonth` or `ZoneId` entity field,
  which the first rule allows in the core. Built as a custom `ArchCondition` over `getFields()`,
  like `mapEnumsToPostgresEnumTypes()`; a violation names the class, field and type.

If the first rule reports a dependency the code cannot avoid (a library method that returns a
`Date`), the developer does not weaken the rule silently: the pull request names the class, and the
document gets the exception with its reason.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: Column type check in the persistence library

- **Depends on**: none
- **Files**:
  - `artifact/backend/library/persistence/src/testFixtures/java/tr/girgin/backend/trading/analysis/platform/library/persistence/ColumnTypeCheck.java` (new)
  - `artifact/backend/library/persistence/src/testFixtures/java/tr/girgin/backend/trading/analysis/platform/library/persistence/DomainPersistenceConventionsTest.java`
  - `artifact/backend/library/persistence/src/test/java/tr/girgin/backend/trading/analysis/platform/library/persistence/ColumnTypeCheckTest.java` (new)
  - `artifact/backend/library/persistence/src/test/resources/tr/girgin/backend/trading/analysis/platform/library/persistence/fixture/types/migration/V1__column_type_violations.sql` (new)
  - `artifact/backend/library/persistence/src/test/resources/tr/girgin/backend/trading/analysis/platform/library/persistence/fixture/sample/migration/V1__create_samples.sql`
  - `artifact/backend/library/persistence/src/test/java/tr/girgin/backend/trading/analysis/platform/library/persistence/fixture/sample/SamplePersistenceConventionsTest.java`
- **Steps**:
  - [ ] Add `ColumnTypeCheck` as described in "Check 1".
  - [ ] Add `columnTypesFollowTheConvention` to `DomainPersistenceConventionsTest`; its class Javadoc
        stays correct (it does not count the tests).
  - [ ] Add the fixture migration in schema `"SAMPLE_TYPES"`, with names that follow the naming
        convention: a table `"TYPES_WRONG"` with a `TIMESTAMP` column, a `TIMETZ` column, an
        `INTERVAL` column and a `TIMESTAMP[]` column; a table `"TYPES_RIGHT"` with a `TIMESTAMPTZ`, a
        `DATE`, a `TIME` and a `BIGINT` column, which yield nothing.
  - [ ] Add `ColumnTypeCheckTest`, built like `DatabaseNamingCheckTest`: migrate the fixture with a
        plain Flyway (`schemas("SAMPLE_TYPES")`, history table `FLYWAY_SCHEMA_HISTORY`), assert the
        exact list of four violations, which also proves the history table's `timestamp` column is
        not reported.
  - [ ] Give the sample domain's `"SAMPLES"` table a `"CREATED_AT" TIMESTAMPTZ` and a `"DUE_DATE"
        DATE` column (it is a test fixture, recreated on every run, so editing its `V1` is allowed),
        so `SamplePersistenceConventionsTest` proves a correct domain with time columns passes the
        new test. Its Javadoc says "passes all four tests": correct the count to five.
- **Tests**: `ColumnTypeCheckTest` (the check reports exactly the fixture's violations and nothing
  for the correct table or Flyway's history table); `SamplePersistenceConventionsTest` (a correct
  domain passes all five tests); `AnalysisPersistenceConventionsTest`,
  `SettingsPersistenceConventionsTest`, `IdentityPersistenceConventionsTest` run the new test on the
  real migrations and pass unchanged. `mise run gradle :backend:library:persistence:test` and the
  three adapter modules' tests (need Docker / Podman with `DOCKER_HOST`).

### WP2: ArchUnit rules for time types

- **Depends on**: none
- **Files**:
  - `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/PersistenceArchitectureTest.java`
- **Steps**:
  - [ ] Add `production_code_uses_only_the_time_types_of_the_convention` and
        `entities_map_time_to_the_convention_types` as described in "Check 2", each with a
        `because(...)` naming backend-java-persistence.md.
- **Tests**: `PersistenceArchitectureTest` passes on the current code
  (`mise run gradle :backend:test --tests '*PersistenceArchitectureTest'`). To prove each rule bites,
  temporarily change `AnalysisEntity.visibilityChangedAt` to `LocalDateTime` and
  `RunStatsEmbeddable.elapsedMs` to `Duration`, see both rules fail with the field named, and revert;
  nothing of that is committed.

### WP3: Documentation and version

- **Depends on**: WP1, WP2
- **Files**:
  - `docs/coding-convention/backend-java-persistence.md`
  - `docs/coding-convention/backend-database-naming.md`
  - `docs/coding-convention/README.md`
  - `gradle.properties`, `artifact/frontend/package.json`, `artifact/ta-runner/ta_runner/__init__.py` (through `mise run version:bump minor`)
- **Steps**:
  - [ ] backend-java-persistence.md: a new section "Time types" after "Mapping domain types" with
        the decision table, the ruled-out types and why, and the time zone rules (see "Design"); the
        "A timestamp" row of the mapping table becomes "A moment, a date, a time of day, a duration"
        and links to the section. "Timestamps: domain facts and audit fields" stays as it is.
  - [ ] backend-java-persistence.md "Shared persistence code" and "Tests": `ColumnTypeCheck` among
        the checks `DomainPersistenceConventionsTest` delegates to; "Its four tests" becomes five,
        with `columnTypesFollowTheConvention` (`ColumnTypeCheck`) described; `ColumnTypeCheck` added to
        the catalog checks that read the catalog with plain JDBC.
  - [ ] backend-java-persistence.md "Where it is checked": rows for the two ArchUnit rules, for
        `columnTypesFollowTheConvention`, and `ColumnTypeCheckTest` added to the row of the library's
        proofs.
  - [ ] backend-database-naming.md "Rules": the column name suffixes for time values (see "Design"),
        marked as not checked, with a link to the persistence document's "Time types" for the types.
  - [ ] docs/coding-convention/README.md: the backend-java-persistence.md row of the documents table
        mentions time types; the "Database naming and schemas" enforcement row mentions column types
        and lists `ColumnTypeCheckTest` among the library's proofs.
  - [ ] `mise run version:bump minor`.
- **Tests**: `mise run check` (formatting, structure, version sync).

## Tests

- **Unit / integration (Testcontainers)**: `ColumnTypeCheckTest` proves the catalog check reports
  every ruled-out column type and nothing else; `SamplePersistenceConventionsTest` proves a correct
  domain with time columns passes; the three domains' conventions tests prove the existing migrations
  follow the rule.
- **ArchUnit**: `PersistenceArchitectureTest` proves the production code and the entities use only
  the allowed Java time types.
- **Frontend, e2e**: none, nothing visible changes.
- The issue's "done when" (decided, written down, existing code checked, enforced where a build check
  can) is proved by `mise run build` passing with the two new checks and by the document.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | docs/coding-convention/backend-java-persistence.md, new section "Time types"; "Mapping domain types" table; "Shared persistence code"; "Tests"; "Where it is checked" | The decision, the ruled-out types, the time zone rules; the new checks and where they run |
| Text | docs/coding-convention/backend-database-naming.md "Rules" | Column name suffixes for time values, not checked |
| Text | docs/coding-convention/README.md, documents table and enforcement table | Time types in the persistence document's scope; column types and `ColumnTypeCheckTest` in the database row |
| Screenshot | none | No page changes |

## Out of scope

- A check that production code takes the time from the `Clock` bean (no `Instant.now()` /
  `LocalDate.now()` without a clock): `DockerRunnerAdapter` still calls `Instant.now()` twice, and
  fixing it means injecting the `Clock` into the runner adapter. A separate issue.
- A check of the column name suffixes (`_AT`, `_DATE`, `_MS`): written down as guidance only;
  `LOCKED_UNTIL` shows that a fixed suffix list would need exceptions.
- The New analysis page computes "today" in the browser's zone (`NewAnalysisPage.vue`,
  `lastWeekday`, `futureDate`), while the backend validates the trade date against today in UTC: a
  user east of UTC who picks today's date after local midnight but before UTC midnight is rejected.
  A frontend and validation change, not a mapping one; see "Open questions".

## Open questions

- Durations: the plan rules out `INTERVAL` and a `Duration` entity field, and keeps the existing
  practice (`Duration` in the core, a `BIGINT` with the unit in the column name). Is that the wanted
  rule, or should a `Duration` map to `INTERVAL`?
- The browser-zone versus UTC "today" of the trade date (see "Out of scope"): open a separate issue
  for it?
