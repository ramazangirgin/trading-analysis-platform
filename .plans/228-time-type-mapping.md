# Plan: Java, Hibernate and SQL types for every persisted value

- **Issue**: #228 (Decide the Java type to Hibernate/SQL mapping for each time type (Instant vs LocalDateTime))
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The persistence convention says, in one table, which Java type the domain core uses, which Java type
the entity uses, how Hibernate maps it and which PostgreSQL column type holds it, for every kind of
value the code stores or may store: text, IDs, booleans, integers, decimal amounts, enums, JSON,
bytes, value sets, moments, dates, times of day and durations. Each row names what is ruled out and
why. The build fails when a migration creates a column type outside the table, or an entity field
uses a Java type outside it. The two columns that break the decision today move with a new Flyway
migration: `ANALYSES.ELAPSED_MS` (a duration as `BIGINT` milliseconds) becomes `ELAPSED INTERVAL`,
and `ANALYSES.COST_USD` (money as `DOUBLE PRECISION`) becomes `NUMERIC`.

Why one pull request and not several plans: docs/coding-convention/README.md "Changing a
convention" asks for the document, the check that enforces it and the code that has to move in one
pull request, and the checks fail until both columns have moved. The change stays reviewable:
4 work packages of at most 7 files, of which one touches production code (one embeddable, two
mappers, one migration); the rest is a test-fixture check, one ArchUnit class and documentation.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend, `:backend:domain:analysis:adapter` (migration, embeddable, mappers) | [backend-java-persistence.md](../docs/coding-convention/backend-java-persistence.md): never edit a merged migration, add one; one migration test per migration that changes data (as `V5AnalysisPartialVisibilityMigrationTest`); [backend-database-naming.md](../docs/coding-convention/backend-database-naming.md) for the renamed column; [backend-java-package-structure.md](../docs/coding-convention/backend-java-package-structure.md) |
| Backend, `:backend:library:persistence` test fixtures and tests | backend-java-persistence.md "Shared persistence code" and "Tests": a catalog check is a package-private class in the test fixtures, reads the catalog with plain JDBC through `CatalogQueries`, is run by `DomainPersistenceConventionsTest`, and is proved by a library test against a fixture migration of a made-up schema (as `DatabaseNamingCheck` / `DatabaseNamingCheckTest` / `fixture/naming/`) |
| Backend, `artifact/backend` ArchUnit tests | `PersistenceArchitectureTest` holds the persistence rules; rule fields are snake_case sentences with a `because(...)` naming the document |
| Docs | [docs/coding-convention/README.md](../docs/coding-convention/README.md) "Changing a convention"; [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md) |

## Design

### The decision (written into backend-java-persistence.md, "Mapping domain types")

The table below replaces the current "Mapping domain types" table; its rows for ID wrappers,
single-value wrappers, embeddables, enums, value sets, references and the row version keep their
current text and examples, and the "Why these choices" bullets stay, with the new reasons added.

| Kind of value | Domain core | Entity field (Hibernate mapping) | Column | Ruled out | In the code today |
|---|---|---|---|---|---|
| Text | `String` (or a single-value wrapper) | `String`, default mapping; a wrapper through `@Convert` | `TEXT` | `VARCHAR(n)`, `CHAR(n)` | every text column |
| An ID | an ID wrapper of a `String` (`AnalysisId`, `UserId`, ...) | `@EmbeddedId` embeddable with one `String value` | `TEXT` | `UUID` column or `java.util.UUID` field; `SERIAL` / identity columns | every `ID` |
| A flag | `boolean` | `boolean` (`Boolean` on a nullable column) | `BOOLEAN` | `CHAR(1)` / integer flags | `ENABLED`, `BUILT_IN`, `CHECKPOINT_ENABLED`, ... |
| A whole number | `int` / `long` | `int` / `long` (`Integer` / `Long` on a nullable column) | `INTEGER` / `BIGINT` | `short` / `SMALLINT` | `MAX_DEBATE_ROUNDS`, `FAILED_LOGIN_COUNT` (`INTEGER`); `TOKENS_IN`, `VERSION` (`BIGINT`) |
| A decimal amount (money, a price, a ratio stored for later) | `BigDecimal` | `BigDecimal`, default mapping | `NUMERIC` (no precision unless the domain fixes a scale: then `NUMERIC(p,s)` and the core rounds to that scale) | `float` / `double` fields, `REAL`, `DOUBLE PRECISION`, `MONEY` | `COST_USD` is `DOUBLE PRECISION` / `Double`: moves to `NUMERIC` / `BigDecimal` (the core is `BigDecimal` already) |
| An enum | the enum | the enum, `@Enumerated(STRING)` + `@JdbcTypeCode(NAMED_ENUM)` (unchanged) | a PostgreSQL enum type of the domain's schema | text or integer columns for an enum | `STATUS`, `RATING`, `VISIBILITY`, ... |
| A set of values without foreign key | `List` / `Set` of an allowed type | the same, `@JdbcTypeCode(ARRAY)` (unchanged) | a one-dimensional array of an allowed column type | arrays of a ruled-out type | `ANALYSTS`, `PERMISSIONS` |
| References to another aggregate | `Set` of the ID wrapper | `@ElementCollection` on a join table (unchanged) | join table of `TEXT` columns | `@ManyToMany` between aggregates | `USER_ROLES` |
| JSON the backend reads into or queries | a record (or `String`) | `@JdbcTypeCode(SqlTypes.JSON)` | `JSONB` | `JSON` | not used |
| A document the backend stores and returns unread | `String` | `String` | `TEXT` | | `PRESETS.PAYLOAD` (the frontend's JSON "stored as is", `Preset`): stays `TEXT`, since `JSONB` would reorder keys and drop whitespace |
| Bytes | `byte[]` | `byte[]`, default mapping | `BYTEA` | `@Lob`, `OID`, `Byte[]` | not used |
| A moment: something happened or ends at a point on the time line | `Instant` | `Instant`, default mapping | `TIMESTAMPTZ` | `LocalDateTime` / `TIMESTAMP`, `OffsetDateTime`, `ZonedDateTime`, `java.util.Date`, `Calendar`, `java.sql.*` | `CREATED_AT`, `STARTED_AT`, `ENDED_AT`, `VISIBILITY_CHANGED_AT`, `UPDATED_AT`, `LAST_LOGIN_AT`, `LOCKED_UNTIL` |
| A calendar date (a market's trading day) | `LocalDate` | `LocalDate`, default mapping | `DATE` | | `ANALYSES.TRADE_DATE` |
| A time of day | `LocalTime` | `LocalTime`, default mapping | `TIME` (without time zone); a zone it belongs to is a separate `TEXT` column holding a zone ID | `OffsetTime` / `TIMETZ` | not used |
| A duration | `Duration` | `Duration` with `@JdbcTypeCode(SqlTypes.INTERVAL_SECOND)` | `INTERVAL` | a number with the unit in the name (`BIGINT` `_MS`); `Duration` without the annotation (it becomes `NUMERIC` nanoseconds) | `ELAPSED_MS` `BIGINT` / `long`: moves to `ELAPSED` `INTERVAL` / `Duration` |

Nullability, as guidance: a primitive field maps a `NOT NULL` column, a wrapper (`Integer`, `Long`,
`Boolean`) a nullable one, since Hibernate cannot put a `NULL` into a primitive.

Reasons for what is ruled out (each goes into the document's "Why these choices"):

- **`VARCHAR(n)` and `CHAR(n)`**: PostgreSQL stores `TEXT` and `VARCHAR` the same way, so the length
  only adds a limit, and a limit is a domain rule: the core checks it and answers with a domain error
  (`PresetService` `MAX_PAYLOAD`) instead of a database error on insert. `CHAR(n)` pads with spaces.
- **`UUID` columns**: IDs are not all UUIDs (`AnalysisId` is `r_` plus hex or an imported run's name,
  built-in role IDs are `ADMIN`, `ANALYST`, ...), and one ID column type keeps every ID wrapper and
  every join the same. **Identity and `SERIAL` columns**: the core makes the ID (`newId()`) before
  the row exists.
- **`SMALLINT`**: saves two bytes and adds a third integer type whose Java arithmetic widens anyway.
- **`float`, `double`, `REAL`, `DOUBLE PRECISION`**: binary fractions, so `0.1` is not stored as
  `0.1`, sums drift and an amount read back is not equal to the amount written. `NUMERIC` keeps the
  decimal exactly; unconstrained `NUMERIC` keeps whatever scale the value has, which LLM costs of
  fractions of a cent need. (In-memory calculations that are not stored, such as the price chart's
  `double` closes, are not covered.)
- **`MONEY`**: its scale and its text form follow the database's `lc_monetary`, and the currency is
  not stored; the currency goes into the column name (`COST_USD`).
- **`JSON`**: stores the text and only validates it; `JSONB` can be indexed and queried. Opaque
  documents stay `TEXT` (above).
- **`@Lob` and `OID`**: on PostgreSQL Hibernate maps a `@Lob` to a large object, stored outside the
  row, not deleted with it and readable only inside a transaction. `BYTEA` is stored in the row.
- **A duration as a number**: the unit lives only in the column name and every mapper converts it;
  `INTERVAL` says what the value is in `psql`, in a migration and in the entity, and Hibernate maps
  it to `Duration` directly.

### Why not `LocalDateTime` / `TIMESTAMP` (written into the document as its own paragraph)

A `LocalDateTime` is a wall-clock reading without a zone: `2026-10-28T09:30` is a different moment
in Istanbul, New York and UTC. A `TIMESTAMP` column stores the same reading, and PostgreSQL converts
it with the session's `TimeZone` whenever it is compared with a `TIMESTAMPTZ` or `now()`. So the
moment a row stands for depends on the zone of whoever wrote it and of whoever reads it: the JVM's
default zone, the container's zone, the database's setting. Nothing in this codebase has the meaning
"a wall-clock time in a zone known from context": every timestamp is a moment (an event happened,
a lock ends), and the trading day is a date with no time. A value that needs a wall-clock reading
stores what it means instead:

- a moment and the zone it was seen in: `Instant` on `TIMESTAMPTZ` plus a `TEXT` column with the
  zone ID (`Europe/Istanbul`);
- a recurring local time ("the market opens at 09:30 exchange time"): `LocalTime` on `TIME` plus the
  zone ID, or `LocalDate` + `LocalTime` when it is on one date.

When an exception would be justified, so the developer can overrule the rule for one column: (a) the
value comes from a source that gives a local date-time and its zone cannot be known, so converting
it to an `Instant` would invent a zone; or (b) a future wall-clock event years ahead in a zone whose
daylight saving rules may change, whose moment must follow those rule changes (then `LocalDateTime`
on `TIMESTAMP` plus a zone ID column). Such an exception is never silent: the document lists the
column and its reason, and `ColumnTypeCheck` and the ArchUnit entity rule get an explicit exemption
naming that table, column and field.

### Durations on `INTERVAL`: what Hibernate does in this repository

Checked against Hibernate ORM 7.4.5.Final (the version Spring Boot 4.1 manages here) and the
repository's settings, which set no `hibernate.type.*` property:

- `DurationJavaType` asks for the JDBC type in `hibernate.type.preferred_duration_jdbc_type`, whose
  default is `SqlTypes.DURATION`. Its `DurationJdbcType` has the DDL type `NUMERIC`: a plain
  `Duration` field is stored as nanoseconds in a `numeric(21)`, and `ddl-auto=validate` rejects it on
  an `INTERVAL` column.
- `@JdbcTypeCode(SqlTypes.INTERVAL_SECOND)` on the field selects the `PostgreSQLDialect`'s
  `PostgreSQLIntervalSecondJdbcType` (binds and reads a `PGInterval`; its DDL type is
  `interval second(6)`), with `PostgreSQLCastingIntervalSecondJdbcType` when the driver's
  `PGInterval` is not available. The column in the migration is a plain `INTERVAL`.
- Per field and not the global property, because it is the same pattern as
  `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`, it is visible where the field is, it needs no second copy in
  `jpa-test.properties` next to `application.properties`, and ArchUnit can check it (below).
- `JpaAnalysisRepositoryAdapterTest` starts JPA with `ddl-auto=validate` on the migrated schema, so
  it proves the mapping validates and round-trips. If validation does not accept the column, the
  field also gets `columnDefinition = "INTERVAL"`, as the enums do; the document then says so.
- Precision: `INTERVAL` keeps microseconds, like `TIMESTAMPTZ`; finer `Duration` values are rounded.
  A duration column holds seconds only: never write a month or year interval (`'1 month'`) into it,
  since a month has no fixed length and the value would not convert to a `Duration`.

### `ELAPSED_MS` and `COST_USD`: migrated, not exempted

Decision: a new migration `V6__store_elapsed_as_interval_and_cost_as_numeric.sql` in the analysis
domain moves both columns; `V1` is merged and stays untouched. Why not keep `ELAPSED_MS` as a legacy
exception: it is the only duration column, so the exception would be the one example a reader finds,
contradicting the rule it sits next to; the catalog check cannot see that a `BIGINT` holds a
duration, so the exception would live only in prose; and `ANALYSES` is one small table, moved with
one `ALTER`. `COST_USD` moves for the same reason and because the money path loses exactness today:
the core holds a `BigDecimal`, the entity a `Double`.

```sql
ALTER TABLE "ANALYSIS"."ANALYSES" RENAME COLUMN "ELAPSED_MS" TO "ELAPSED";
ALTER TABLE "ANALYSIS"."ANALYSES" ALTER COLUMN "ELAPSED" DROP DEFAULT;
ALTER TABLE "ANALYSIS"."ANALYSES" ALTER COLUMN "ELAPSED" TYPE INTERVAL USING "ELAPSED" * INTERVAL '1 millisecond';
ALTER TABLE "ANALYSIS"."ANALYSES" ALTER COLUMN "ELAPSED" SET DEFAULT INTERVAL '0';
ALTER TABLE "ANALYSIS"."ANALYSES" ALTER COLUMN "COST_USD" TYPE NUMERIC USING "COST_USD"::NUMERIC;
```

The column loses its unit suffix because the type carries the unit. The cast of `DOUBLE PRECISION`
to `NUMERIC` keeps 15 significant digits, more than any stored cost has. Impact:

- `RunStatsEmbeddable`: `elapsedMs` (`long`, `ELAPSED_MS`) becomes `Duration elapsed` on `ELAPSED`
  with `@JdbcTypeCode(SqlTypes.INTERVAL_SECOND)`; `Double costUsd` becomes `BigDecimal costUsd`.
- `RunStatsToRunStatsEmbeddableMapper` and `RunStatsEmbeddableToRunStatsMapper` map by name and drop
  `DurationToMillisMapper` / `MillisToDurationMapper` and their `@Mapping`; `MillisToDurationMapper`
  (analysis adapter) is deleted, since nothing else uses it. `DurationToMillisMapper` in
  `library:mapper` stays (the BFF and `JsonlEventStoreAdapter` use the library), so the analysis
  adapter's dependency declarations do not change.
- REST API and OpenAPI: none. `RunStatsDto` keeps `elapsedMs` (`long`) and `costUsd`
  (`BigDecimal`); `openapi.json` and `schema.d.ts` do not change. The event protocol does not change
  (`cost_usd`, `elapsed_s` are parsed as before).
- The migration tests of V2 to V5 and the seed `V1_1__seed_rows_before_version.sql` insert
  `ELAPSED_MS` before their target version and then migrate to the latest one, so V6 runs on their
  rows; they need no change.
- The branch carries a migration: run it only against a throwaway PostgreSQL, never the shared dev
  database (CLAUDE.md). On deploy, V6 rewrites `ANALYSES` once.

### Time zone rules (backend-java-persistence.md, same section)

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
- Precision: `TIMESTAMPTZ` and `INTERVAL` keep microseconds and round finer values, so a value that
  is compared after it was read back is cut to microseconds before it is saved
  (`ClockDateTimeProvider`), or to the precision of its source (`Analysis#millis` for imported runs).

### Column name suffixes (backend-database-naming.md "Rules", guidance, not checked)

A moment ends in `_AT`, or `_UNTIL` for the end of a period (`LOCKED_UNTIL`); a date in `_DATE`; a
duration says what was measured (`ELAPSED`) and carries no unit, since `INTERVAL` has one; an amount
of one currency ends in the currency (`COST_USD`).

### The existing code against the decision

Every column and entity field of the analysis, identity and settings migrations and entities was
checked: all follow the table except `ELAPSED_MS` and `COST_USD`, which move (above).
`PRESETS.PAYLOAD` is an opaque document on `TEXT` and follows its row. Nullable columns map to
wrappers or objects (`RATING`, `COST_USD`, `LOCKED_UNTIL`, ...), `NOT NULL` ones to primitives or
objects. Production code uses no ruled-out time type (`LocalDateTime` nowhere; `ZoneOffset`,
`ZoneId`, `java.util.Date` in tests and test fixtures only).

### Check 1: column types in the catalog (`ColumnTypeCheck`)

A new package-private `ColumnTypeCheck` in the test fixtures of `:backend:library:persistence`, next
to `DatabaseNamingCheck` and built like it: `static List<String> violations(DataSource, String
schema)` reads the columns of the schema's base tables through `CatalogQueries.query`
(`information_schema.columns` for `data_type`, `udt_name`, `is_identity`, `column_default`, and
`pg_type` for whether a user-defined type, or an array's element type, is an enum). It is an
allowlist: a column passes when its type is `text`, `bool`, `int4`, `int8`, `numeric`, `date`,
`time`, `timestamptz`, `interval`, `jsonb`, `bytea` or an enum type, or a one-dimensional array of
one of these (`interval[]` included). It is reported when its type is anything else, when it is an
identity column, or when its default calls `nextval(`. `FLYWAY_SCHEMA_HISTORY` is exempt: Flyway
creates it with `varchar`, `timestamp` and `int4` columns and that cannot be configured (the same
exemption as in the naming check). One sorted line per violation, in the naming check's style, with
the replacement for the known ruled-out types:

```
column "CODE" of table "SAMPLE_TYPES"."TYPES_WRONG": character, use TEXT (String)
column "DUE" of table "SAMPLE_TYPES"."TYPES_WRONG": timestamp without time zone, use TIMESTAMPTZ (Instant)
column "RATE" of table "SAMPLE_TYPES"."TYPES_WRONG": double precision, use NUMERIC (BigDecimal)
column "SEQ" of table "SAMPLE_TYPES"."TYPES_WRONG": identity column, the core makes the ID
column "TAGS" of table "SAMPLE_TYPES"."TYPES_WRONG": xml, not a column type of backend-java-persistence.md
```

Replacements: `timestamp` → `TIMESTAMPTZ (Instant)`; `timetz` → `TIME (LocalTime)`; `varchar`,
`bpchar` → `TEXT (String)`; `float4`, `float8`, `money` → `NUMERIC (BigDecimal)`; `int2` →
`INTEGER (int)`; `json` → `JSONB`; `oid` → `BYTEA (byte[])`; `uuid` → `TEXT (an ID wrapper)`; an
array names its element type the same way; any other type gets "not a column type of
backend-java-persistence.md".

`DomainPersistenceConventionsTest` gets a fifth test, `columnTypesFollowTheConvention`: migrate with
the domain's Flyway bean on a fresh database (as `namesFollowTheConvention`), then assert
`ColumnTypeCheck.violations(dataSource, schema)` is empty, with the message "column types that break
backend-java-persistence.md". The three domains' conventions tests pick it up without a change.

### Check 2: Java types (ArchUnit, `PersistenceArchitectureTest`)

A "Types" group with three rules:

- `production_code_uses_only_the_time_types_of_the_convention`: no class in `ROOT` depends on
  `LocalDateTime`, `OffsetDateTime`, `ZonedDateTime`, `OffsetTime`, `java.util.Date`,
  `java.util.Calendar` (subclasses included) or `java.util.TimeZone`. `java.sql` is already banned by
  `production_code_does_not_use_plain_sql`. The importer already leaves out tests and test fixtures,
  so the tests' `java.util.Date` (Spring Session) and fixed clocks stay allowed.
- `entity_fields_use_the_types_of_the_convention`: every persistent field (not static, not
  `@Transient`) of an `@Entity`, `@Embeddable` or `@MappedSuperclass` has a raw type of `String`,
  `boolean` / `Boolean`, `int` / `Integer`, `long` / `Long`, `BigDecimal`, `byte[]`, `Instant`,
  `LocalDate`, `LocalTime`, `Duration`, an enum or an `@Embeddable` class; or is annotated `@Convert`
  (a single-value wrapper); or is a `List` / `Set` annotated `@JdbcTypeCode` (an array) or
  `@ElementCollection`. No field is annotated `@Lob`. A violation names the class, field and type.
  This rules out `LocalDateTime`, `float` / `double`, `short`, `UUID`, `Date` and every other type in
  an entity.
- `durations_map_to_interval`: a `Duration` field of an entity or embeddable carries
  `@JdbcTypeCode(SqlTypes.INTERVAL_SECOND)`, since without it Hibernate maps it to `NUMERIC`
  nanoseconds (see above).

The two field rules are custom `ArchCondition`s over `getFields()`, like
`mapEnumsToPostgresEnumTypes()`. If the first rule reports a dependency the code cannot avoid (a
library method that returns a `Date`), the developer does not weaken the rule silently: the pull
request names the class, and the document gets the exception with its reason.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: Move `ELAPSED_MS` to `INTERVAL` and `COST_USD` to `NUMERIC`

- **Depends on**: none
- **Files**:
  - `artifact/backend/domain/analysis/adapter/src/main/resources/tr/girgin/backend/trading/analysis/platform/domain/analysis/adapter/persistence/migration/V6__store_elapsed_as_interval_and_cost_as_numeric.sql` (new)
  - `artifact/backend/domain/analysis/adapter/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/analysis/adapter/persistence/entity/RunStatsEmbeddable.java`
  - `artifact/backend/domain/analysis/adapter/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/analysis/adapter/persistence/mapper/RunStatsToRunStatsEmbeddableMapper.java`
  - `artifact/backend/domain/analysis/adapter/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/analysis/adapter/persistence/mapper/RunStatsEmbeddableToRunStatsMapper.java`
  - `artifact/backend/domain/analysis/adapter/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/analysis/adapter/persistence/mapper/MillisToDurationMapper.java` (deleted)
  - `artifact/backend/domain/analysis/adapter/src/test/java/tr/girgin/backend/trading/analysis/platform/domain/analysis/adapter/persistence/V6AnalysisColumnTypesMigrationTest.java` (new)
  - `artifact/backend/domain/analysis/adapter/src/test/java/tr/girgin/backend/trading/analysis/platform/domain/analysis/adapter/persistence/JpaAnalysisRepositoryAdapterTest.java`
- **Steps**:
  - [ ] Add V6 as in "`ELAPSED_MS` and `COST_USD`", with a header comment saying why (the convention
        of backend-java-persistence.md), as the other migrations do.
  - [ ] `RunStatsEmbeddable`: `Duration elapsed` on `ELAPSED` with
        `@JdbcTypeCode(SqlTypes.INTERVAL_SECOND)`, `BigDecimal costUsd`; accessors renamed.
  - [ ] Both mappers map by name, without `uses` and `@Mapping`; delete `MillisToDurationMapper`.
  - [ ] Add `V6AnalysisColumnTypesMigrationTest`, built like `V5AnalysisPartialVisibilityMigrationTest`:
        a Flyway stopped at V5 inserts a row with `ELAPSED_MS` 261500 and `COST_USD` 0.0277 and one
        with `COST_USD` NULL, then migrates to the latest version.
  - [ ] `JpaAnalysisRepositoryAdapterTest`: a run with elapsed `PT4M21.5S` and cost `0.000123`
        saved and read back equal (cost by `compareTo`).
- **Tests**: `V6AnalysisColumnTypesMigrationTest` (rows written at V5 read back with
  `Duration.ofMillis(261500)`, cost equal to `0.0277`, a null cost stays null);
  `JpaAnalysisRepositoryAdapterTest` (the `INTERVAL` mapping passes `ddl-auto=validate`, and a
  duration and a fraction-of-a-cent cost round-trip exactly); the V2 to V5 migration tests,
  `AnalysisPersistenceConventionsTest`, `AnalysesApiIntegrationTest` and
  `ImportExistingRunsUseCaseTest` pass unchanged.
  `mise run gradle :backend:domain:analysis:adapter:test` and `mise run gradle :backend:test`
  (Docker / Podman with `DOCKER_HOST`).

### WP2: Column type check in the persistence library

- **Depends on**: WP1 (the analysis domain's conventions test fails on `float8` until then)
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
        convention: an enum type `"SAMPLE_TYPES"."TYPES_MOOD"`; a table `"TYPES_WRONG"` with one
        column per ruled-out case (`TIMESTAMP`, `TIMETZ`, `VARCHAR(20)`, `CHAR(3)`, `REAL`,
        `DOUBLE PRECISION`, `MONEY`, `SMALLINT`, `JSON`, `OID`, `UUID`, `XML`, `TIMESTAMP[]`, a
        `BIGINT GENERATED ALWAYS AS IDENTITY` and a `BIGSERIAL`); a table `"TYPES_RIGHT"` with
        `TEXT`, `BOOLEAN`, `INTEGER`, `BIGINT`, `NUMERIC`, `NUMERIC(12,4)`, `DATE`, `TIME`,
        `TIMESTAMPTZ`, `INTERVAL`, `JSONB`, `BYTEA`, the enum, an array of the enum, `TEXT[]` and
        `INTERVAL[]` columns, which yield nothing.
  - [ ] Add `ColumnTypeCheckTest`, built like `DatabaseNamingCheckTest`: migrate the fixture with a
        plain Flyway (`schemas("SAMPLE_TYPES")`, history table `FLYWAY_SCHEMA_HISTORY`), assert the
        exact sorted list, one line per `TYPES_WRONG` column, which also proves the history table's
        columns are not reported.
  - [ ] Give the sample domain's `"SAMPLES"` table `"CREATED_AT" TIMESTAMPTZ`, `"DUE_DATE" DATE`,
        `"ELAPSED" INTERVAL`, `"AMOUNT" NUMERIC` and `"DETAILS" JSONB` columns (it is a test
        fixture, recreated on every run, so editing its `V1` is allowed), so
        `SamplePersistenceConventionsTest` proves a correct domain with these columns passes the new
        test. Its Javadoc says "passes all four tests": correct the count to five.
- **Tests**: `ColumnTypeCheckTest` (the check reports exactly the fixture's wrong columns, and
  nothing for the correct table, its interval array included, or for Flyway's history table);
  `SamplePersistenceConventionsTest` (a correct domain passes all five tests);
  `AnalysisPersistenceConventionsTest`, `SettingsPersistenceConventionsTest`,
  `IdentityPersistenceConventionsTest` run the new test on the real migrations and pass unchanged.
  `mise run gradle :backend:library:persistence:test` and the three adapter modules' tests.

### WP3: ArchUnit rules for Java types

- **Depends on**: WP1 (`RunStatsEmbeddable.costUsd` is a `Double` until then)
- **Files**:
  - `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/PersistenceArchitectureTest.java`
- **Steps**:
  - [ ] Add `production_code_uses_only_the_time_types_of_the_convention`,
        `entity_fields_use_the_types_of_the_convention` and `durations_map_to_interval` as described
        in "Check 2", each with a `because(...)` naming backend-java-persistence.md.
- **Tests**: `PersistenceArchitectureTest` passes on the current code
  (`mise run gradle :backend:test --tests '*PersistenceArchitectureTest'`). To prove each rule bites,
  temporarily change `AnalysisEntity.visibilityChangedAt` to `LocalDateTime`,
  `RunStatsEmbeddable.costUsd` to `Double` and remove the `@JdbcTypeCode` from
  `RunStatsEmbeddable.elapsed`, see the three rules fail with the field named, and revert; nothing
  of that is committed.

### WP4: Documentation and version

- **Depends on**: WP1, WP2, WP3
- **Files**:
  - `docs/coding-convention/backend-java-persistence.md`
  - `docs/coding-convention/backend-database-naming.md`
  - `docs/coding-convention/README.md`
  - `gradle.properties`, `artifact/frontend/package.json`, `artifact/ta-runner/ta_runner/__init__.py` (through `mise run version:bump minor`)
- **Steps**:
  - [ ] backend-java-persistence.md "Mapping domain types": the table of "The decision" replaces the
        current one (keeping its existing rows' text and examples), "Why these choices" gets the
        reasons for what is ruled out, and a sub-section "Time values" holds "Why not
        `LocalDateTime`" with its exception criteria, the `INTERVAL` mapping (Hibernate's default
        and the `@JdbcTypeCode`), and the time zone rules. "Timestamps: domain facts and audit
        fields" stays as it is.
  - [ ] backend-java-persistence.md "Shared persistence code" and "Tests": `ColumnTypeCheck` among
        the checks `DomainPersistenceConventionsTest` delegates to and the catalog checks that read
        the catalog with plain JDBC; "Its four tests" becomes five, with
        `columnTypesFollowTheConvention` described.
  - [ ] backend-java-persistence.md "Where it is checked": rows for the three ArchUnit rules and for
        `columnTypesFollowTheConvention`; `ColumnTypeCheckTest` added to the row of the library's
        proofs.
  - [ ] backend-database-naming.md "Rules": the column name suffixes (see "Design"), marked as not
        checked, with a link to the persistence document's "Mapping domain types" for the types.
  - [ ] docs/coding-convention/README.md: the backend-java-persistence.md row of the documents table
        mentions column and Java types; the "Database naming and schemas" enforcement row mentions
        column types and lists `ColumnTypeCheckTest` among the library's proofs.
  - [ ] `mise run version:bump minor`.
- **Tests**: `mise run check` (formatting, structure, version sync).

## Tests

- **Unit / integration (Testcontainers)**: `V6AnalysisColumnTypesMigrationTest` proves existing rows
  keep their elapsed time and cost through V6; `JpaAnalysisRepositoryAdapterTest` proves the
  `INTERVAL` and `NUMERIC` mappings validate and round-trip exactly; `ColumnTypeCheckTest` proves
  the catalog check reports every ruled-out column type and nothing else;
  `SamplePersistenceConventionsTest` proves a correct domain passes; the three domains' conventions
  tests prove the migrations follow the rule.
- **ArchUnit**: `PersistenceArchitectureTest` proves production code uses only the allowed time
  types, entity fields only the types of the table, and durations map to `INTERVAL`.
- **Frontend, e2e**: none, nothing visible changes (the API keeps `elapsedMs` and `costUsd`).
- The issue's "done when" (decided, written down, existing code checked, enforced where a build check
  can) is proved by `mise run build` passing with the new checks and by the document.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | docs/coding-convention/backend-java-persistence.md "Mapping domain types" (table, "Why these choices", new "Time values"); "Shared persistence code"; "Tests"; "Where it is checked" | The complete type table, the ruled-out types and why, why not `LocalDateTime` and when an exception is justified, the `INTERVAL` mapping, the time zone rules; the new checks and where they run |
| Text | docs/coding-convention/backend-database-naming.md "Rules" | Column name suffixes, not checked; durations carry no unit suffix |
| Text | docs/coding-convention/README.md, documents table and enforcement table | Column and Java types in the persistence document's scope; column types and `ColumnTypeCheckTest` in the database row |
| OpenAPI | none | `RunStatsDto` does not change |
| Screenshot | none | No page changes |

## Out of scope

- A check that production code takes the time from the `Clock` bean (no `Instant.now()` /
  `LocalDate.now()` without a clock): `DockerRunnerAdapter` still calls `Instant.now()` twice, and
  fixing it means injecting the `Clock` into the runner adapter. A separate issue.
- A check of the column name suffixes (`_AT`, `_DATE`, `_USD`) and of primitive versus wrapper
  fields by nullability: written down as guidance only; `LOCKED_UNTIL` shows that a fixed suffix
  list would need exceptions.
- The New analysis page computes "today" in the browser's zone (`NewAnalysisPage.vue`,
  `lastWeekday`, `futureDate`), while the backend validates the trade date against today in UTC: a
  user east of UTC who picks today's date after local midnight but before UTC midnight is rejected.
  A frontend and validation change, not a mapping one; the developer opens a separate issue for it.
- The runner and the event protocol: `cost_usd` and `elapsed_s` stay JSON numbers.

## Open questions

None.
