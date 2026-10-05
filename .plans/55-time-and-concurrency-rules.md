# Plan: Project Checkstyle rules, part 4: time and concurrency (T, C)

- **Issue**: #55 (Java: project-specific Checkstyle rules (custom checks))
- **Plan**: 4 of 7 for this issue; depends on: `55-checkstyle-rules-module` (the module,
  `ForbiddenMemberAccessCheck`, the test and doc setup) and `55-configuration-properties` (it
  rewrites the constructors this plan adds a `Clock` to)
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

Backend code no longer reads the system time directly: services and adapters get an injected
`java.time.Clock`, so tests can fix time. The build fails on `Instant.now()`-style calls, legacy date
types, unseeded randomness, `Thread.sleep`, `new Thread(` / `Executors.new…`, and `synchronized`
methods on Spring beans, each with a `TAP-T*` / `TAP-C*` id. The existing code is changed to follow
the rules, or carries a suppression with a reason where the rule's exception applies.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend (`artifact/backend`) | `docs/coding-convention/backend-java-package-structure.md`: `:backend` holds `TradingPlatformApplication` only; services stay package-private in `…core.service`; adapters at their port package root. `backend-java-checkstyle.md`: `ParameterNumber` 7, suppressions in the code with a reason |
| Custom checks module, Checkstyle config, docs | The contract from plan 1: generic checks with documented parameters, a reference per check, index and catalogue rows, `ProjectRulesTest` fixtures per rule, 100% coverage |

## Design

### One `Clock` bean

`TradingPlatformApplication` gets `@Bean Clock clock()` returning `Clock.systemDefaultZone()`.
No new class: `:backend`'s root package may only hold the application class
(`module_root_packages_hold_no_classes`), and `@SpringBootApplication` is a configuration class.
`systemDefaultZone()` and not `systemUTC()`, because `AnalysisService` validates the spec against
`LocalDate.now()` in the JVM's zone today; `Instant.now(clock)` does not depend on the zone. With an
instance method on the class, its `HideUtilityClassConstructor` suppression is no longer needed and
is removed.

Who gets the clock (constructor injection, after plan 3 replaced `@Value` by settings records; tests pass `Clock.fixed(...)` or a mutable test clock):

| Class | Today | After |
|---|---|---|
| `domain.settings.core.service.PresetService` | `Instant.now()` ×2 | `Instant.now(clock)` |
| `domain.analysis.core.service.AnalysisService` | `Instant.now()` ×4, `LocalDate.now()` | `Instant.now(clock)`, `LocalDate.now(clock)` |
| `domain.catalog.core.service.CatalogService` | `Instant.now()` ×2 in the nested cache | the cache reads the service's clock |
| `domain.analysis.adapter.runner.DockerRunnerAdapter` | `Instant.now()` for the leftover-container cutoff | `Instant.now(clock)`; both constructors take the clock |
| `domain.analysis.adapter.eventline.RunnerOutputLineParser` | `Instant.now()` as the fallback timestamp | `RunnerOutputLineParser(Clock clock)`; `DockerRunnerAdapter`, `ProcessRunnerAdapter` and `JsonlEventStoreAdapter` construct it with their injected clock |

`ImportExistingRunsService` already takes a `Clock` (`Clock.systemUTC()` in its `@Autowired`
constructor). With the settings record from plan 3 it injects the `Clock` bean instead, and its
second, test-only constructor goes away unless a test still needs it. `DataDirWatchService`'s
`System::nanoTime` supplier measures elapsed time, is injectable already, and is not covered by T1.

### Rules

| Id | Rule | Check and parameters | Sources | Hits in main today → fix |
|---|---|---|---|---|
| TAP-T1 | No `Instant` / `LocalDate` / `LocalDateTime` / `LocalTime` / `OffsetDateTime` / `ZonedDateTime` `.now()` and no `System.currentTimeMillis()`; inject a `Clock` and call `now(clock)` | `ForbiddenMemberAccessCheck`: `members = Instant#now(0), LocalDate#now(0), LocalDateTime#now(0), LocalTime#now(0), OffsetDateTime#now(0), ZonedDateTime#now(0), System#currentTimeMillis()` | main | 11 in 5 files → Clock (above) |
| TAP-T2 | No `java.util.Date`, `java.util.Calendar`, `java.text.SimpleDateFormat`, `java.sql.Timestamp`, `java.sql.Date`; use `java.time`. A persistence adapter that needs one suppresses it with a reason | built-in `IllegalImport` (own instance), `illegalClasses` as listed | main and test | 0 |
| TAP-T3 | No `new Random(` / `Math.random()`. `SecureRandom` for ids stays allowed (`AnalysisId`) | `ForbiddenMemberAccessCheck`: `members = java.util.Random#new(), Math#random()` | main | 0 |
| TAP-C1 | No `Thread.sleep` / `TimeUnit.X.sleep` in main sources; use a scheduled executor or a retry helper | `ForbiddenMemberAccessCheck`: `members = Thread#sleep(), TimeUnit.*#sleep()` (a receiver pattern with `*` matches `TimeUnit.SECONDS`) | main | 2 → suppressed (below) |
| TAP-C2 | No `new Thread(` and no `Executors.new…(`; threads are virtual and named (`Thread.ofVirtual().name(...)`), an executor is owned by a bean that shuts it down | `ForbiddenMemberAccessCheck`: `members = Thread#new(), Executors#new*()` | main | 2 → suppressed (below) |
| TAP-C3 | No `synchronized` methods on Spring beans (`@Service`, `@Component`, `@Repository`, `@Controller`, `@RestController`, `@Configuration`); lock a private object, so callers cannot take the bean's lock | `ForbiddenModifierCheck` (new, below): `modifiers = synchronized`, `targets = METHOD`, `inTypesAnnotatedWith` = the six stereotypes (qualified). Nested helper classes (`AnalysisEventHub`'s subscriber, `CatalogService`'s cache, `DataDirWatchService`'s debouncer) are not beans and are not matched | main | 8 → refactored (below) |

### `ForbiddenModifierCheck` (new)

| Parameter | Type | Default | Meaning |
|---|---|---|---|
| `modifiers` | list of Java modifiers | (required) | Modifiers to forbid (`synchronized`, `public`, `static`, ...) |
| `targets` | list of `METHOD`, `FIELD`, `CONSTRUCTOR`, `CLASS` | all | Declaration kinds checked |
| `inTypesAnnotatedWith` | list of type names | empty (every type) | Only members declared directly in a type carrying one of these annotations (simple or qualified, resolved through imports); members of nested types are judged by the nested type's own annotations |
| `suggestion` | string | empty | Appended to the message |

The `main`-only rules are switched off for test sources with a `SuppressionSingleFilter` whose `id`
is `^TAP-(T1|T3|C1|C2|C3)$` and whose `files` match `[\\/]src[\\/]test[\\/]`, next to the existing
`MissingJavadocPortType` filter. It is a scope, like that one, not a suppression.

### Existing hits

- **C1** (`DockerRunnerAdapter` reconnect pause, `ProcessRunnerAdapter` follow interval): both are
  polling loops on their own named virtual thread, where waiting between polls is the design.
  Suppressed on the method with `@SuppressWarnings("checkstyle:TAP-C1") // polls on its own virtual
  thread`.
- **C2** (`ImportExistingRunsService.followUps`, `DataDirWatchService.watch`): both create a
  single-thread scheduled executor on a virtual thread factory and shut it down (`destroy()`, the
  returned `AutoCloseable`). Suppressed with that reason. The eight `Thread.ofVirtual()` uses are
  not matched.
- **C3**: each class gets `private final Object lock = new Object();` and its `synchronized` methods
  become `synchronized (lock) { ... }` blocks, so all of a class's critical sections keep sharing one
  lock and behaviour does not change: `ImportExistingRunsService` (3 methods),
  `SecretsService` (2), `DotenvSecretStoreAdapter` (1), `ExternalAnalysisService` (1),
  `JsonlEventStoreAdapter` (1). `AnalysisService` already locks a private object.

## Work packages

### WP1: Clock injection

- **Depends on**: none
- **Files**: `artifact/backend/src/main/java/.../TradingPlatformApplication.java`,
  `PresetService.java`, `AnalysisService.java`, `CatalogService.java`, `DockerRunnerAdapter.java`,
  `ProcessRunnerAdapter.java`, `JsonlEventStoreAdapter.java`, `RunnerOutputLineParser.java`,
  `ImportExistingRunsService.java`, and the tests constructing them: `ImportExistingRunsServiceTest`,
  `AnalysisServiceTest`, `CatalogServiceTest`, `DockerRunnerAdapterTest`,
  `ProcessRunnerAdapterTest`, `JsonlEventStoreAdapterTest`, `RunnerOutputLineToRunEventMapperTest`,
  `Fakes` (if it builds services)
- **Steps**:
  - [ ] Add the `Clock` bean; drop the `HideUtilityClassConstructor` suppression.
  - [ ] Inject the clock into the classes in the table and replace every `now()` call.
  - [ ] Update the tests to pass a fixed clock.
- **Tests**:
  - `CatalogServiceTest`: the cached catalog is served until `cache-minutes` pass and refetched
    after, using a test clock moved forward (new; replaces any sleeping or real-time dependence).
  - `AnalysisServiceTest`: a spec dated after the clock's date is rejected and one on that date is
    accepted (new), and `queued` / `finished` timestamps equal the fixed clock's instant.
  - `RunnerOutputLineToRunEventMapperTest`: a line without a timestamp gets the clock's instant (new).
  - `TradingPlatformApplicationTests` / `AnalysesApiIntegrationTest` start the context with the bean.

### WP2: Lock objects instead of synchronized bean methods

- **Depends on**: none
- **Files**: `ImportExistingRunsService.java`, `SecretsService.java`,
  `DotenvSecretStoreAdapter.java`, `ExternalAnalysisService.java`, `JsonlEventStoreAdapter.java`
- **Steps**:
  - [ ] Replace each `synchronized` method by a block on a private lock, as in Design.
- **Tests**: the existing tests of these classes (`ImportExistingRunsServiceTest`,
  `SecretsServiceTest`, `DotenvSecretStoreAdapterTest`, `ExternalAnalysisServiceTest`,
  `JsonlEventStoreAdapterTest`) pass unchanged.

### WP3: `ForbiddenModifierCheck`

- **Depends on**: none (plan 1 merged)
- **Files**: `build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/ForbiddenModifierCheck.java`,
  `messages.properties`, `ForbiddenModifierCheckTest.java`, `src/test/resources/checks/ForbiddenModifierCheck/*`
- **Steps**:
  - [ ] Implement with parameter validation and the shared name resolution.
  - [ ] Tests: each target kind; a `synchronized` block (not a modifier, not reported); a
        `synchronized` method in a nested class of an annotated type (not reported) and in a nested
        type that is itself annotated (reported); simple and qualified annotation names; empty
        `inTypesAnnotatedWith`; invalid modifier names.
- **Tests**: as listed; 100% coverage.

### WP3b: Rules T1 to T3, C1 to C3

- **Depends on**: WP1, WP2, WP3
- **Files**: `config/checkstyle/checkstyle.xml`, `src/test/resources/rules/TAP-T1/*` ... `TAP-C3/*`,
  `DockerRunnerAdapter.java`, `ProcessRunnerAdapter.java`, `ImportExistingRunsService.java`,
  `DataDirWatchService.java`
- **Steps**:
  - [ ] Add the six instances and the main-only scope filter.
  - [ ] Add the C1 and C2 suppressions with their reasons.
  - [ ] Fixtures for `ProjectRulesTest`, with the near misses as compliant code: `Instant.now(clock)`,
        `new SecureRandom()`, `Thread.ofVirtual()`, a `synchronized` method in a nested class of a
        `@Service`; and, for the main-only rules, a violation placed in a test source that must not
        be reported.
  - [ ] Backend Checkstyle clean.
- **Tests**: `ProjectRulesTest`.

### WP4: Documentation

- **Depends on**: WP3b
- **Files**: `docs/coding-convention/backend-java-checkstyle.md`,
  `docs/coding-convention/backend-java-checkstyle-checks/ForbiddenModifierCheck.md` (new),
  `docs/coding-convention/backend-java-checkstyle-checks/ForbiddenMemberAccessCheck.md`,
  `docs/coding-convention/backend-java-checkstyle-custom-checks.md`
- **Steps**:
  - [ ] Reference document for `ForbiddenModifierCheck` with configuration examples for at least
        two conditions (C3, and e.g. no `public` fields in `@Entity` types).
  - [ ] `ForbiddenMemberAccessCheck.md`: its "used by" list gains T1, T3, C1, C2; the index rows follow.
  - [ ] Catalogue rows T1 to T3, C1 to C3: id, rule,
        reason, sources, check and parameters, bad / good example (e.g. `Instant.now()` → `Instant.now(clock)` with the
        `Clock` bean; `synchronized` method → private lock).
  - [ ] Mention the main-only scope filter next to the `MissingJavadocType` / `MagicNumber` scopes
        in "Suppressing a finding".
  - [ ] Suppressions table: add `TAP-C1` (runner adapters' polling loops), `TAP-C2` (the two
        scheduled executors); remove the `HideUtilityClassConstructor` / `TradingPlatformApplication`
        row.
- **Tests**: none.

## Tests

`mise run build` passes: `ForbiddenModifierCheck`'s unit tests at 100% coverage, `ProjectRulesTest`
with the T and C fixtures, `DocumentationTest`, Checkstyle clean with the new rules, the new clock-based tests in
`CatalogServiceTest`, `AnalysisServiceTest` and `RunnerOutputLineToRunEventMapperTest`, and every
existing test, including the Spring context tests. `mise run e2e` is not needed (no behaviour change
visible from the UI), but the developer agent runs it if the runner adapters' changes touch the
replay path.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-checkstyle.md` | Catalogue rows T and C, main-only scope, suppressions table |
| Text | `docs/coding-convention/backend-java-checkstyle-checks/` | New `ForbiddenModifierCheck.md`; `ForbiddenMemberAccessCheck.md` "used by" |
| Text | `docs/coding-convention/backend-java-checkstyle-custom-checks.md` | New index row; rules column of `ForbiddenMemberAccessCheck` |
| Text | `docs/coding-convention/backend-java-package-structure.md` | None: `TradingPlatformApplication` stays the only class in `:backend` |
| Screenshot | none | No UI change |

## Out of scope

- Replacing the two polling loops or the two scheduled executors by shared executor beans: a
  larger refactor of the runner adapters, not needed for the rules (suppressed with a reason).
- `Thread.sleep` in tests: plan 6 (`TAP-X4`).
