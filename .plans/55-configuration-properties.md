# Plan: Project Checkstyle rules, part 3: Spring and dependency injection (D), typed configuration records

- **Issue**: #55 (Java: project-specific Checkstyle rules (custom checks))
- **Plan**: 3 of 7 for this issue; depends on: `55-checkstyle-rules-module`
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md). The
  property keys and their defaults do not change, so configuration stays compatible.

## Goal

`@Value` is gone from the backend. Every setting a class reads comes from a typed
`@ConfigurationProperties` record, bound through the record's constructor, so a class gets one
settings object instead of a list of `@Value` strings, and defaults sit in one place per setting
group. Checkstyle forbids `@Value` with a message that says what to use instead, injection anywhere
but a constructor, `RestTemplate`, and a method-level `@RequestMapping` without an HTTP method.
The rules are instances of two new generic checks: `ForbiddenAnnotationCheck` (forbid an annotation,
everywhere or on given declaration kinds, with a suggestion) and `RequiredAnnotationAttributeCheck`
(an annotation must set given attributes). Forbidding another annotation later is one config block,
no code.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend (`artifact/backend`) | `backend-java-package-structure.md`: known sub-package kinds per layer, records in adapters only in data packages, modules never share classes (accepted duplicates), `:backend` holds `TradingPlatformApplication` only. This plan **adds a package kind**, `properties` (see Design and Open questions) |
| ArchUnit (`ArchitectureTest`) | Placement rules for the new kind, next to the existing ones; a rule that selects nothing passes silently, so each new rule is shown failing once |
| Custom checks module, Checkstyle config, docs | The contract from plan 1: generic checks, parameters documented in a reference per check, index row, catalogue rows, `ProjectRulesTest` fixtures per rule, 100% coverage |
| Build (`gradle/libs.versions.toml`, module `build.gradle.kts`) | Versions from the catalog; Spring Boot's BOM manages Boot artifacts |

## Design

### `ForbiddenAnnotationCheck` (new)

| Parameter | Type | Default | Meaning |
|---|---|---|---|
| `annotations` | list of type names | (required) | Annotations to forbid, simple or qualified. A qualified name is matched by the qualified use and by the simple name when the file imports it (plan 1's `support/` resolution), so an unrelated `Value` annotation is not reported |
| `targets` | list of `CLASS`, `INTERFACE`, `RECORD`, `ENUM`, `ANNOTATION_TYPE`, `FIELD`, `METHOD`, `CONSTRUCTOR`, `PARAMETER`, `CONSTRUCTOR_PARAMETER`, `RECORD_COMPONENT`, `LOCAL_VARIABLE` | all | Declaration kinds on which the annotation is forbidden; elsewhere it is allowed |
| `suggestion` | string | empty | Appended to the message |

### `RequiredAnnotationAttributeCheck` (new)

| Parameter | Type | Default | Meaning |
|---|---|---|---|
| `annotation` | type name | (required) | The annotation, simple or qualified (resolved as above) |
| `attributes` | list | (required) | Attributes that must be set. `value` is also satisfied by the single-value form `@Disabled("why")` |
| `targets` | list (as above) | all | Declaration kinds where the requirement applies (a class-level `@RequestMapping("/api")` is allowed, a method-level one is not) |
| `nonEmpty` | boolean | `true` | An empty string or empty array does not count as set |
| `suggestion` | string | empty | Appended to the message |

Plan 6 reuses it for `@Disabled` (X2).

### Rules

| Id | Rule | Check and parameters | Sources | Hits today |
|---|---|---|---|---|
| TAP-D1 | `@Autowired` / `@Inject` only on constructors | `ForbiddenAnnotationCheck`: `annotations = org.springframework.beans.factory.annotation.Autowired, jakarta.inject.Inject`, `targets = FIELD, METHOD, PARAMETER` | main and test | 0 (the 4 `@Autowired` are on constructors) |
| TAP-D2 | No `@Value`; bind settings with a `@ConfigurationProperties` record | `ForbiddenAnnotationCheck`: `annotations = org.springframework.beans.factory.annotation.Value` (all targets) | main and test | every `@Value` today → replaced (below) |
| TAP-D4a | No `RestTemplate`; use `RestClient` | built-in `IllegalImport` (own instance), `illegalClasses = org.springframework.web.client.RestTemplate` | main and test | 0 |
| TAP-D4b | A handler method's `@RequestMapping` names its HTTP method; prefer `@GetMapping` ... | `RequiredAnnotationAttributeCheck`: `annotation = org.springframework.web.bind.annotation.RequestMapping`, `attributes = method`, `targets = METHOD` | main | 0 (the 5 uses are class-level) |

D3 (no Spring stereotypes in `core/model`) depends on packages, so it is an ArchUnit rule: plan 7.

### Configuration records

- One `*Properties` record per class that reads settings today, holding exactly the keys that
  class reads, annotated `@ConfigurationProperties("<longest common prefix>")`. Defaults that are in
  `@Value("${key:default}")` today move to `@DefaultValue` on the component, unchanged. A value built
  from a key (`${platform.data-dir}/reports`) is derived in the consuming class
  (`dataDir.resolve("reports")`). `String[]` settings become `List<String>`, bound from the same
  comma-separated values.
- **Package kind `properties`**: `domain.<d>.core.properties`, `domain.<d>.adapter.<port>.properties`,
  `orchestration.<feature>.properties`. Records only, named `*Properties`, public (another package of
  the same module injects them).
- A key read by several modules (`platform.home`, `platform.runner.process.*`,
  `platform.secrets.*`) is bound by a record in each module that reads it, the same way converters
  are duplicated today: modules stay independent. Spring binds a prefix into several records
  without conflict.
- `TradingPlatformApplication` gets `@ConfigurationPropertiesScan`, so every record under the base
  package is registered.
- Core and orchestration modules depend on `spring-context` only today, and `@ConfigurationProperties`
  / `@DefaultValue` are in `spring-boot`: `domain:*:core` (analysis, report, catalog) and
  `orchestration` get `implementation("org.springframework.boot:spring-boot")` through a new catalog
  entry `spring-boot-core` without a version (BOM-managed). Only the `context.properties` annotations
  are used; ArchUnit's `domain_core_does_not_depend_on_infrastructure` stays as it is.

| Class reading settings today | Record (package) | Prefix | Keys |
|---|---|---|---|
| `AnalysisService` | `AnalysisProperties` (`domain.analysis.core.properties`) | `platform.analysis` | `max-concurrent-runs` (2) |
| `CatalogService` | `CatalogProperties` (`domain.catalog.core.properties`) | `platform.catalog` | `cache-minutes` (10) |
| `DataDirWatchService` | `DataDirWatchProperties` (`domain.report.core.properties`) | `platform.import.watch` | `quiet-period-ms` (5000), `max-delay-seconds` (60) |
| `ImportExistingRunsService` | `ImportProperties` (`orchestration.report.properties`) | `platform.import` | `on-startup` (true), `watch.enabled` (true), `settle-minutes` (10) |
| `ProcessRunnerAdapter`, `DockerRunnerAdapter` | `RunnerProperties` (`domain.analysis.adapter.runner.properties`) | `platform` | `home`; `runner.stop-grace-seconds` (15), `runner.follow-interval-ms` (500); `runner.process.command`, `runner.process.working-dir`; `runner.docker.host`, `.image`, `.data-mount`, `.network` (""), `.memory-mb` (2048), `.cpus` (2), `.tmp-size-mb` (512), `.log-silence-minutes` (10) |
| `JsonlEventStoreAdapter` | `EventStoreProperties` (`…adapter.eventstore.properties`) | `platform` | `home` |
| `RunLogFileAdapter` | `RunLogProperties` (`…adapter.runlog.properties`) | `platform` | `home` |
| `EnvFileCredentialsAdapter` | `CredentialsProperties` (`…adapter.credentials.properties`) | `platform.secrets` | `env-file`, `external-env-files` (empty) |
| `TaRunnerEngineInfoAdapter`, `DockerEngineInfoAdapter` | `EngineRunnerProperties` (`domain.catalog.adapter.runner.properties`) | `platform.runner` | `process.command`, `process.working-dir`, `docker.host`, `docker.image` |
| `FileSystemDataDirAdapter`, `FileSystemDataDirWatchAdapter` | `DataDirProperties` (`domain.report.adapter.datadir.properties`) | `platform` | `data-dir`, `results-dir` |
| `JsonRunHistoryAdapter` | `RunHistoryProperties` (`…adapter.history.properties`) | `platform` | `data-dir` |
| `CsvPriceCacheAdapter` | `PriceCacheProperties` (`…adapter.prices.properties`) | `platform` | `cache-dir` |
| `DotenvSecretStoreAdapter` | `SecretStoreProperties` (`domain.settings.adapter.secrets.properties`) | `platform.secrets` | `env-file`, `external-env-files` (empty) |

The table is the starting point; the developer may merge two records of the same port package if
they read the same keys. Records nest for sub-prefixes (`RunnerProperties.Runner.Docker`).

**Watch out**: `platform.runner` is both a value (`process` / `docker`, read by the runner
condition in `support`) and the parent of `platform.runner.docker.*`. A record bound at `platform`
with a nested `runner` component, or at `platform.runner`, must still bind; the binding test below
covers it. If Boot refuses, the condition keeps reading the scalar and the record binds
`platform.runner.docker` / `platform.runner.process` separately.

Constructors get shorter: the `ParameterNumber` suppressions on `DockerRunnerAdapter`'s
constructors ("one @Value per runner setting") and on `ImportExistingRunsService` go away where the
count drops to 7 or less. The test-only constructors that take plain values (`DockerRunnerAdapter`
with a `DockerClient`, `ImportExistingRunsService` with a `Clock`) take the record instead.

### ArchUnit

- `adapter_sub_packages_are_known_kinds`, `domain_core_sub_packages_are_known_kinds`,
  `orchestration_features_follow_the_domain_core_shape`: accept `properties`.
- `adapter_records_live_in_data_packages`: accept `..properties..`.
- New `properties_live_in_properties_packages`: classes annotated `@ConfigurationProperties` reside
  in `..properties..`.
- New `properties_packages_hold_only_properties_records`: top-level classes in `..properties..` are
  records, annotated `@ConfigurationProperties`, named `*Properties`.

## Work packages

### WP1: Records, binding and constructor changes

- **Depends on**: none
- **Files**: `gradle/libs.versions.toml`; `build.gradle.kts` of `domain/{analysis,report,catalog}/core`
  and `orchestration`; new `*Properties` records (table); the 16 classes in the table;
  `TradingPlatformApplication.java`; tests constructing those classes (`AnalysisServiceTest`,
  `CatalogServiceTest`, `DataDirWatchServiceTest`, `ImportExistingRunsServiceTest`,
  `DockerRunnerAdapterTest`, `ProcessRunnerAdapterTest`, `JsonlEventStoreAdapterTest`,
  `EnvFileCredentialsAdapterTest`, `TaRunnerEngineInfoAdapterTest`, `DockerEngineInfoAdapterTest`,
  `FileSystemDataDirAdapterTest`, `FileSystemDataDirWatchAdapterTest`, `JsonRunHistoryAdapterTest`,
  `CsvPriceCacheAdapterTest`, `DotenvSecretStoreAdapterTest`, `Fakes`)
- **Steps**:
  - [ ] Add the catalog entry and module dependencies.
  - [ ] Create the records; replace every `@Value` parameter by the record.
  - [ ] `@ConfigurationPropertiesScan` on the application class.
  - [ ] Remove `ParameterNumber` suppressions that are no longer needed.
  - [ ] Update the tests to build records.
- **Tests**:
  - New `ConfigurationPropertiesBindingTest` in `:backend` (`src/test/.../`): starts the context
    (as `AnalysesApiIntegrationTest` does, with `TestPlatformHome`) and asserts every record's values
    from `application.properties`, including each default that `@Value` had and the `platform.runner`
    scalar-plus-children case. This proves the keys and defaults did not change.
  - All existing tests pass with records.

### WP2: ArchUnit rules for the `properties` kind

- **Depends on**: none (can be written with WP1)
- **Files**: `artifact/backend/src/test/java/.../ArchitectureTest.java`
- **Steps**:
  - [ ] Change and add the rules listed in Design.
  - [ ] Show each new rule failing on a deliberately misplaced record (PR description).
- **Tests**: `ArchitectureTest`.

### WP3: `ForbiddenAnnotationCheck` and `RequiredAnnotationAttributeCheck`

- **Depends on**: none (plan 1 merged)
- **Files**: `build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/ForbiddenAnnotationCheck.java`,
  `RequiredAnnotationAttributeCheck.java`, `messages.properties`, their test classes and
  `src/test/resources/checks/<CheckName>/*`
- **Steps**:
  - [ ] Implement both, with parameter validation and the shared name resolution.
  - [ ] Tests: every target kind (including a constructor parameter versus a method parameter, and a
        compact record constructor); simple, qualified and fully written annotation uses; an
        unrelated annotation with the same simple name and no import (not reported); `value` set
        by the single-value form; empty string / empty array with `nonEmpty` on and off; invalid
        target names.
- **Tests**: as listed; 100% coverage.

### WP3b: Rules D1, D2, D4

- **Depends on**: WP1 (no `@Value` left), WP3
- **Files**: `config/checkstyle/checkstyle.xml`, `src/test/resources/rules/TAP-D1/*`, `TAP-D2/*`,
  `TAP-D4a/*`, `TAP-D4b/*`
- **Steps**:
  - [ ] Add the four instances; fixtures for `ProjectRulesTest`.
  - [ ] Backend Checkstyle clean.
- **Tests**: `ProjectRulesTest`.

### WP4: Documentation

- **Depends on**: WP1 to WP3b
- **Files**: `docs/coding-convention/backend-java-checkstyle-checks/ForbiddenAnnotationCheck.md` (new),
  `docs/coding-convention/backend-java-checkstyle-checks/RequiredAnnotationAttributeCheck.md` (new),
  `docs/coding-convention/backend-java-checkstyle-custom-checks.md`,
  `docs/coding-convention/backend-java-checkstyle.md`,
  `docs/coding-convention/backend-java-package-structure.md`
- **Steps**:
  - [ ] Reference documents for both checks from the template, each with configuration examples
        for at least two conditions (e.g. `@Value` everywhere and `@Autowired` on fields only;
        `@RequestMapping` needs `method` and `@Disabled` needs a reason).
  - [ ] Index rows for both checks.
  - [ ] Catalogue rows D1, D2, D4a, D4b with reason and a bad / good example (`@Value` parameter →
        record + injection); D3 → ArchUnit (plan 7).
  - [ ] Checkstyle doc: suppressions table without the removed `ParameterNumber` entries.
  - [ ] Package structure doc: the `properties` kind in the package layout tree, the "Where each
        class kind goes" table (with the two new rule names and the changed ones), the adapter
        packages table (new `properties` sub-packages), and the accepted-duplicates table (records
        binding the same keys in several modules, and why).
- **Tests**: none.

## Tests

`mise run build`: the two checks' unit tests at 100% coverage, `ProjectRulesTest` with the D
fixtures, `DocumentationTest`, ArchUnit with the new rules, Checkstyle clean, the new binding test,
all existing tests. `mise run e2e` runs the built jar with the real `application.properties`, so it also
proves the app starts and runs a replayed analysis with the records; the developer agent runs it.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-checkstyle-checks/` | Two new reference documents |
| Text | `docs/coding-convention/backend-java-checkstyle-custom-checks.md` | Two index rows |
| Text | `docs/coding-convention/backend-java-checkstyle.md` | Catalogue rows D1, D2, D4a, D4b; suppressions table |
| Text | `docs/coding-convention/backend-java-package-structure.md` | `properties` package kind, rules, duplicates |
| Text | Root `README.md`, `deploy/` | None: property keys, environment variables and defaults are unchanged |
| Screenshot | none | No UI change |

## Out of scope

- Renaming or regrouping property keys (would be a breaking configuration change).
- Validation of the settings (`@Validated` with constraints): could follow, not needed here.

## Open questions

- Reading the request "use only @ConstructorProperties" as Spring Boot's `@ConfigurationProperties`
  with constructor binding (records). `java.beans.ConstructorProperties` plays no part in Spring's
  configuration binding.
- The domain cores and orchestration gain a dependency on `spring-boot` (for two annotations). The
  alternative keeps them on `spring-context` only: plain records in core, bound by `@Bean` methods
  annotated `@ConfigurationProperties` in a configuration class in `:backend` (which then needs a
  second class there, against `module_root_packages_hold_no_classes`' intent). The plan takes the
  dependency.
