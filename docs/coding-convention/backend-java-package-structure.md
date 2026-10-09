# Backend: Java package structure

Where each kind of class lives in the Spring Boot backend (`artifact/backend`), and which ArchUnit
rule in [`ArchitectureTest`](../../artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/ArchitectureTest.java)
guards it (the persistence and shared-library rules: in
[`PersistenceArchitectureTest`](../../artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/PersistenceArchitectureTest.java)). The rule names below are the field names in that class.

## Modules

The backend is a modular monolith: a BFF in front of hexagonal domains, plus `orchestration` for use
cases spanning several domains. Every box is a Gradle subproject, so the classpath enforces the
dependencies between modules first and `ArchitectureTest` second.

All packages start with `tr.girgin.backend.trading.analysis.platform` (written `…` below).

| Gradle project | Root package | Holds |
|---|---|---|
| `:backend:bff:api` | `….bff.controller.api` | REST/SSE controllers, delegate interfaces, web DTOs |
| `:backend:bff:impl` | `….bff.delegate.impl` | Delegate implementations and their mappers |
| `:backend:orchestration` | `….orchestration` | Use cases spanning several domains, one sub-package per feature |
| `:backend:domain:<d>:core` | `….domain.<d>.core` | The domain: model, ports, services |
| `:backend:domain:<d>:adapter` | `….domain.<d>.adapter` | Outbound adapters, one sub-package per port |
| `:backend:library:<library>` | `….library.<library>` | A shared library: technical code only, no domain type ([Shared libraries](#shared-libraries)) |
| `:backend` | `…` | `TradingPlatformApplication` and the application-wide `Clock` bean; assembles the modules at runtime |

Domains: `analysis`, `report`, `catalog`, `settings`, `identity`.

## Package layout

```
bff/controller/api/
  *ApiController, *ApiDelegate     controllers and the delegate interfaces they call
  error/                           ApiException, ApiExceptionHandler
  config/                          web configuration (SpaWebConfig)
  model/                           web DTOs (*Dto, *Request)
bff/delegate/impl/
  *ApiDelegateImpl                 delegate implementations: call inbound ports, map
  mapper/                          DTO <-> domain mappers and their scalar converters
  mapper/error/                    *ExceptionToApiExceptionMapper
domain/<d>/core/
  model/                           value types, records, enums
  exception/                       *Exception and their error-code enums (*Error)
  inbound/                         *UseCase interfaces (and the types they hand out)
  outbound/<port>/                 *Port interfaces (and the types they exchange)
  service/                         *Service implementations of the use cases, package-private
domain/<d>/adapter/
  <port>/                          one package per outbound port, e.g. persistence, runner
    *Adapter                       the adapter(s) implementing the port: the package root only
    *JpaRepository                 Spring Data repositories, next to their adapter (persistence only)
    entity/    *Entity, *Embeddable, *AttributeConverter   JPA table shapes (backend-java-persistence.md)
    json/      *Json, ...          JSON shapes read or written by the adapter
    spec/      ...                 data handed to an external process (RunnerSpec)
    mapper/    *To*Mapper          MapStruct mappers for this port
    support/   ...                 helpers: clients, file tails, Spring conditions
orchestration/<feature>/           same shape as a domain core
  inbound/ service/ model/
library/<library>/                 a shared library, one kind of technical code (library/mapper:
                                   MapStruct mappers only)
```

A module's root package (`….bff`, `….domain.<d>`, `….domain.<d>.core`, `….domain.<d>.adapter`,
`….orchestration`, `….orchestration.<feature>`, `….library`) holds only its `package-info.java`. A
library's own root (`….library.mapper`) holds its classes directly, since a library is one kind of
class.

Today's adapter packages:

| Package | Root | Sub-packages |
|---|---|---|
| `domain.analysis.adapter.credentials` | `EnvFileCredentialsAdapter` | |
| `domain.analysis.adapter.eventline` | `RunnerOutputLineParser` (shared, see below) | `json`, `mapper` |
| `domain.analysis.adapter.eventstore` | `JsonlEventStoreAdapter` | `mapper` |
| `domain.analysis.adapter.persistence` | `JpaAnalysisRepositoryAdapter`, `AnalysisJpaRepository` | `entity`, `mapper` |
| `domain.analysis.adapter.runlog` | `RunLogFileAdapter` | |
| `domain.analysis.adapter.runner` | `ProcessRunnerAdapter`, `DockerRunnerAdapter` | `spec`, `mapper`, `support` |
| `domain.catalog.adapter.runner` | `TaRunnerEngineInfoAdapter`, `DockerEngineInfoAdapter` | `json`, `mapper`, `support` |
| `domain.identity.adapter.password` | `DelegatingPasswordHasherAdapter` | |
| `domain.identity.adapter.persistence` | `JpaRoleRepositoryAdapter`, `JpaUserRepositoryAdapter`, `RoleJpaRepository`, `UserJpaRepository` | `entity`, `mapper` |
| `domain.report.adapter.datadir` | `FileSystemDataDirAdapter`, `FileSystemDataDirWatchAdapter` | `json` |
| `domain.report.adapter.history` | `JsonRunHistoryAdapter` | |
| `domain.report.adapter.prices` | `CsvPriceCacheAdapter` | |
| `domain.settings.adapter.persistence` | `JpaPresetRepositoryAdapter`, `PresetJpaRepository` | `entity`, `mapper` |
| `domain.settings.adapter.secrets` | `DotenvSecretStoreAdapter` | |

`eventline` is the one adapter package that implements no port: it parses runner output for both
the `runner` and the `eventstore` adapters. Its root holds the parser, its entry point. Shared
packages like it are listed in `ArchitectureTest.SHARED_ADAPTER_PACKAGES`.

## Where each class kind goes

| Class kind | Place | Rule |
|---|---|---|
| Controller, delegate interface | `bff.controller.api` (root) | `controller_root_holds_only_controllers_and_delegates` |
| `ApiException`, `@RestControllerAdvice` | `bff.controller.api.error` | `exceptions_live_in_exception_packages`, `exception_packages_hold_only_exceptions_and_error_codes` |
| Delegate implementation `*ApiDelegateImpl` | `bff.delegate.impl` (root), nothing else there | `delegate_impls_live_at_the_delegate_root`, `delegate_root_holds_only_delegate_impls` |
| MapStruct mapper | an adapter's `…mapper` package, `bff.delegate.impl.mapper` or `library.mapper` (the shared mapper library) | `mappers_live_at_layer_boundaries` |
| Anything in a `mapper` package | is a MapStruct mapper (or its generated `*Impl`) | `mapper_packages_hold_only_mappers` |
| `*ExceptionToApiExceptionMapper` | `bff.delegate.impl.mapper.error`, nothing else there | `exception_mappers_live_in_mapper_error`, `mapper_error_holds_only_exception_mappers` |
| `@Entity` `*Entity`, `@Embeddable` `*Embeddable`, `*AttributeConverter` | `domain.<d>.adapter.<port>.entity`, nothing else there | `entities_live_in_entity_packages`, `entity_packages_hold_only_entities`, `persistence_classes_are_named_by_kind` |
| Spring Data repository `*JpaRepository` | `domain.<d>.adapter.persistence`, next to its adapter | `spring_data_repositories_live_in_persistence_roots`, `port_package_roots_hold_only_adapters` |
| Any record in an adapter | `json` or `spec` | `adapter_records_live_in_data_packages` |
| Adapter sub-packages | only `mapper`, `entity`, `json`, `spec`, `support` | `adapter_sub_packages_are_known_kinds` |
| A class in a shared library | depends on other libraries only, never on domain, BFF or orchestration code | `libraries_depend_only_on_libraries` |
| Two mappers with one simple name | none: a converter two modules need lives once, in `library.mapper` | `mappers_are_not_duplicated_across_modules` |
| Class implementing an outbound port | the root of its `adapter.<port>` package | `port_adapters_live_at_the_port_package_root` |
| Root of an `adapter.<port>` package | port implementations only (shared packages aside) | `port_package_roots_hold_only_adapters` |
| `*UseCase` | `…inbound` | `use_cases_live_in_inbound_packages` |
| `*Service` | `…service` | `services_live_in_service_packages` |
| Exception (anything `Throwable`) | `domain.<d>.core.exception` or `bff.controller.api.error` | `exceptions_live_in_exception_packages` |
| Error-code enum `*Error` | `domain.<d>.core.exception`, next to its exception | `error_codes_live_next_to_their_exception` |
| Domain core sub-packages | only `inbound`, `outbound`, `service`, `model`, `exception` | `domain_core_sub_packages_are_known_kinds` |
| Orchestration feature sub-packages | only `inbound`, `service`, `model` | `orchestration_features_follow_the_domain_core_shape` |
| Classes in a module root package | none | `module_root_packages_hold_no_classes` |

The placement rules sit next to the dependency rules that were there before (BFF uses inbound
ports only, adapters are used only by themselves, domains are independent, only `adapter.runner`
starts processes or talks to Docker, mappers are `SourceToTargetMapper` with one `map` method, ...).
Services, orchestrators included, are used only from their own `service` package
(`domain_services_are_only_used_by_themselves`, `orchestration_services_are_only_used_by_themselves`).

Rules check top-level classes, without `package-info` and without nested types (a record nested in
a `*Json` record belongs to it). Tests are not checked.

## Visibility

Classes stay package-private unless another package needs them. Splitting a port package into
sub-packages makes some of them public: the entities, JSON shapes, specs, support classes and the
mappers the adapter calls. The rest stays package-private: services, adapters, mappers used only by
other mappers of the same `mapper` package, `SpaWebConfig`.

`public` does not open a class to other modules: nothing compiles against an adapter or `bff.impl`
module (`:backend` only has them on its runtime classpath), and ArchUnit's
`adapters_are_only_used_by_themselves` and `bff_impl_is_only_used_by_itself` would catch it anyway.

## Shared libraries

Code that several modules need goes into a shared library, `:backend:library:<library>`, never into
a copy. `:backend:library` is a parent folder with no code of its own, like `:backend:domain`. Each
library is its own Gradle module with its own root package, so a module depends only on the
libraries it uses:

| Gradle project | Root package | Holds | Used by |
|---|---|---|---|
| `:backend:library:mapper` | `….library.mapper` | Generic MapStruct scalar mappers: `DurationToMillisMapper`, `EnumToLowerCaseNameMapper` | `bff:impl`, `domain:analysis:adapter` |
| `:backend:library:persistence` | `….library.persistence` | The JPA auditing configuration (`JpaAuditingConfiguration`, `ClockDateTimeProvider`), and in its test fixtures the shared test code of the persistence adapters ([persistence](backend-java-persistence.md#shared-persistence-code)) | `domain:settings:adapter`, `domain:identity:adapter`, `:backend` (runtime); the persistence adapters' and `:backend`'s tests |

- **Technical code only, never domain logic.** A library holds generic building blocks with no
  project type, no domain concept and no business rule, one kind of code per library. It may depend
  on another library, never on anything else of the project: the classpath enforces it, and so does
  `libraries_depend_only_on_libraries`. Anything that knows about an analysis, a user, a preset or
  another domain term stays in its domain, even when two modules end up with similar code.
  `EnumToLowerCaseNameMapper` is shared because it knows nothing about run events: the mappings that
  apply it to `RunEventType` (`qualifiedByName = "lowerCaseName"`) stay in the analysis adapter and
  the BFF.
- **One user stays local.** A generic converter that one module needs stays in that module's
  `mapper` package. Once a second module needs it, it moves to `:backend:library:mapper`. Within one
  module, a converter shared by several ports goes to `<module root>.mapper.common` (none needs to
  today).
- **Shared test code lives in test fixtures** (Gradle's `java-test-fixtures`) of the library or module
  whose code it supports. It is never copied and never put in a test-only main module. Gradle keeps
  test fixtures off every main classpath; the ArchUnit tests do not import them.
- **Adding a library**: a new module under `artifact/backend/library/<library>`, a row in the table
  above, and a placement rule for its kind of class in `ArchitectureTest` (`library.mapper` is
  covered by `mapper_packages_hold_only_mappers`).

Alternatives considered for sharing code across modules:
- a `mapper.common` package per module only shares within that module;
- the domain cores have no MapStruct;
- adapters must not depend on the BFF;
- a shared MapStruct `@MapperConfig(uses = …)` would hide which converters a mapper uses;
- a JPA converter for `Duration` would not cover the other duplicates.

## Accepted duplicates

Code with a domain meaning is not shared between modules, since a shared module would couple
modules that are kept independent on purpose. These duplicates are accepted:

| Class | In | Why it is not shared |
|---|---|---|
| `DockerClients`, `RunnerKind` | `domain.analysis.adapter.runner.support`, `domain.catalog.adapter.runner.support` | `domains_are_independent`; both are a few lines, and the analysis copy has more (`isNoSuchContainer`). Runner support, not a generic library (yet) |
| `Rating` | `domain.analysis.core.model`, `domain.report.core.model` | `domains_are_independent`; the orchestration maps between them |

No two mappers share a simple name (`mappers_are_not_duplicated_across_modules`): the BFF's
`StringToAnalysisIdMapper`, which turns an invalid ID into a 404, is the only one left, since the
persistence adapter maps IDs through `AnalysisIdEmbeddable`.

Not enforced: a maximum number of classes per package. The DTO package and the BFF mapper package
are large because the API is; the sub-package rules keep each package to one kind of class instead.
