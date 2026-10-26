# Plan: Shared MapStruct config, enforced by Checkstyle

- **Issue**: #227 (Shared MapStruct config: deep cloning and failing on unmapped fields, enforced by Checkstyle)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

Every MapStruct mapper of the backend (66 today, in the BFF, four domain adapters and the shared
mapper library) uses one `@MapperConfig`, `SharedMapperConfig` in `:backend:library:mapper`. It
makes MapStruct deep-clone what it maps (a mapped object never shares a mutable object with its
source), fails the compile on an unmapped target property and on an unmapped source property, and
holds the component model and injection strategy that today are compiler arguments. A `@Mapper`
without `config = ...` fails the build through a new project rule `TAP-M1`, an instance of a new
generic custom check.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend (`artifact/backend`) | [backend-java-package-structure.md](../docs/coding-convention/backend-java-package-structure.md): a library holds technical code only and names no domain, not even in its tests (made-up names); `library.mapper` holds only MapStruct types (`mapper_packages_hold_only_mappers`); mappers are `SourceToTargetMapper` with one `map` method; a shared config must not carry `uses` ("a shared `@MapperConfig(uses = …)` would hide which converters a mapper uses"). [repository-dependency-hygiene.md](../docs/coding-convention/repository-dependency-hygiene.md): a module that now references `SharedMapperConfig` declares `:backend:library:mapper`. [backend-java-checkstyle.md](../docs/coding-convention/backend-java-checkstyle.md): no Javadoc needed on mappers or the config; Palantir Java Format |
| Custom checks (`build-logic/checkstyle-rules`) | [its README](../build-logic/checkstyle-rules/README.md): a generic check with no project knowledge, parameters as setters with a `suggestion`, messages in `messages.properties`, names matched through imports (`support/TypeName`, `Imports`, `SourceNames`), fixtures with `// violation` markers, 100% coverage, a reference document from `docs/_template.md`, a row in the README's check table (`DocumentationTest`) |
| Checkstyle config (`config/checkstyle/checkstyle.xml`) | [backend-java-checkstyle.md, Project rules](../docs/coding-convention/backend-java-checkstyle.md#project-rules): a rule goes in at `error` with its findings fixed in the same pull request, fixtures under `rules/<id>/` (`ProjectRulesTest`), a row in the rules catalogue |
| Build logic (`build-logic/src/main/kotlin`) | the `tradinganalysisplatform.mapstruct` convention plugin keeps only what a `@MapperConfig` cannot set |
| Docs | the library's `README.md`, the package-structure and Checkstyle conventions |

## Design

### The shared configuration

In `:backend:library:mapper`, package `tr.girgin.backend.trading.analysis.platform.library.mapper`:

- `SharedMapperConfig`, a public interface annotated
  `@MapperConfig(componentModel = MappingConstants.ComponentModel.SPRING, injectionStrategy = InjectionStrategy.CONSTRUCTOR, unmappedTargetPolicy = ReportingPolicy.ERROR, unmappedSourcePolicy = ReportingPolicy.ERROR, mappingControl = DeepCloneWithConversions.class)`.
  No `uses`: every mapper keeps naming its own converters.
- `DeepCloneWithConversions`, a public annotation type (`@Retention(CLASS)`) meta-annotated
  `@MappingControl(MappingControl.Use.MAPPING_METHOD)` and
  `@MappingControl(MappingControl.Use.BUILT_IN_CONVERSION)`. It is MapStruct's `DeepClone` (only
  mapping methods, no direct assignment of a mapped object, no complex mapping) plus MapStruct's
  built-in conversions, which always create a new immutable value (`String`, `BigDecimal`, ...),
  so they cannot share state with the source.

  Why not `DeepClone` itself (decided with the developer): a trial run during planning (all 66 mappers with `DeepClone`) failed
  four mappers on built-in conversions that `DeepClone` forbids:
  `SecretStatusToSecretStatusDtoMapper` and `SystemHealthToSystemHealthDtoMapper` (enum to
  `String`), `RunStatsEmbeddableToRunStatsMapper` / `RunStatsToRunStatsEmbeddableMapper` (`Double`
  ↔ `BigDecimal`), `AnalysisSpecToRunnerSpecMapper` (`LocalDate` to `String`). With
  `DeepCloneWithConversions` the whole backend compiles and the tests that ran pass;
  `AnalysisDefaultsApiIntegrationTest` did not start (no container engine) and is to be confirmed
  by `mise run build` (WP15).

`DurationToMillisMapper` and `EnumToLowerCaseNameMapper` use the config too. The library's test
source set gets the MapStruct processor (`testAnnotationProcessor`) so a test can generate a mapper
with made-up types and prove the deep copy.

### Unmapped source properties

`unmappedSourcePolicy = ERROR` makes every source property either mapped or explicitly left out.
The trial run found five mappers that leave source properties out on purpose; each declares them
with `@BeanMapping(ignoreUnmappedSourceProperties = {...})` on its `map` method:

| Mapper | Source properties left out | Why |
|---|---|---|
| `RunEventToRunEventDtoMapper` (BFF) | `rating`, `outcome`, `stats`, `error` | `RunEventDto` has no such fields |
| `AnalysisEntityToAnalysisMapper` (analysis persistence) | `new` | Spring Data's `Persistable.isNew()`, not a domain value |
| `UserToUserEntityMapper` (identity persistence) | `createdAt`, `updatedAt` | set by auditing, never from the domain (the targets are ignored already) |
| `PresetToPresetEntityMapper` (settings persistence) | `updatedAt` | the same |
| `AnalysisDefaultsEntityToAnalysisDefaultsMapper` (settings persistence) | `id` | the row key of the single defaults row, not part of the domain model |

Every other mapper compiles unchanged except for `config = SharedMapperConfig.class` (and its
import) on `@Mapper`, merged into the existing `@Mapper(uses = ...)` where there is one.

### Compiler arguments

`tradinganalysisplatform.mapstruct.gradle.kts` passes `defaultComponentModel=spring`,
`defaultInjectionStrategy=constructor` and `unmappedTargetPolicy=ERROR`. Once every mapper uses the
config (which overrides them) and `TAP-M1` enforces it, these three are removed in the last package
so the settings live in one place; `suppressGeneratorTimestamp=true` stays (a `@MapperConfig` cannot
set it). They stay until then so that the packages in between build.

### Placement rules (ArchUnit)

`ArchitectureTest` (`artifact/backend/src/test/java/.../ArchitectureTest.java`):

- `mapper_packages_hold_only_mappers` also admits a type annotated `@MapperConfig` and an annotation
  type meta-annotated `@MappingControl` (predicate next to `MAPSTRUCT_MAPPER`).
- New `mapper_configs_live_in_the_mapper_library`: classes annotated `@MapperConfig` (and annotation
  types meta-annotated `@MappingControl`) reside in `LIBRARY_MAPPER`, so the config a mapper names
  can only be the shared one.

### The Checkstyle check and rule

A new generic check `tr.girgin.checkstyle.RequiredAnnotationAttributeCheck` in
`build-logic/checkstyle-rules`: reports an annotation of one of the configured types that does not
set every one of the required attributes.

- Parameters: `annotations` (comma-separated types, simple or qualified, required), `attributes`
  (comma-separated attribute names, required, each a Java identifier), `suggestion`.
- Token: `ANNOTATION`; the type is matched with `TypeName.matches` through `Imports.of(rootAST)` as
  in `StaticMethodInAnnotatedClassCheck`, so `@Mapper`, `@org.mapstruct.Mapper` and an on-demand
  import match, and an `@other.Mapper` does not. The set attributes are the `IDENT`s of its
  `ANNOTATION_MEMBER_VALUE_PAIR` children; a single-element annotation (`@A(x)`) sets `value`. A
  marker annotation (`@Mapper`) sets none.
- One finding per missing attribute, on the annotation. Message key
  `required.annotation.attribute`: `{0}Annotation ''@{1}'' does not set ''{2}''.{3}` (id prefix,
  annotation as written, attribute, suggestion).
- Invalid or missing parameters fail `finishLocalSetup` with an `IllegalArgumentException` naming
  the parameter.

Project rule `TAP-M1` at the end of `TreeWalker` in `config/checkstyle/checkstyle.xml`, main and
test sources:

```xml
<module name="tr.girgin.checkstyle.RequiredAnnotationAttributeCheck">
    <property name="id" value="TAP-M1"/>
    <property name="annotations" value="org.mapstruct.Mapper"/>
    <property name="attributes" value="config"/>
    <property name="suggestion" value="Use the shared configuration: @Mapper(config = SharedMapperConfig.class)."/>
</module>
```

`M` is a new id group (MapStruct and mapping), decided with the developer; the catalogue's group
list gets it.

## Work packages

Packages without a dependency between them can be implemented in parallel. WP3 to WP13 are the
same mechanical change in different modules: add `config = SharedMapperConfig.class` to `@Mapper`
(merged into an existing `@Mapper(uses = ...)`), import `SharedMapperConfig`, and nothing else
unless the step says so. Each runs `mise run gradle :backend:<module>:test` for its module.

### WP1: ArchUnit placement rules for the config

- **Status**: done
- **Depends on**: none
- **Files**: `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/ArchitectureTest.java`, `docs/coding-convention/backend-java-package-structure.md`
- **Steps**:
  - [x] Widen `mapper_packages_hold_only_mappers` to admit `@MapperConfig` types and annotation types meta-annotated `@MappingControl`.
  - [x] Add `mapper_configs_live_in_the_mapper_library` (both kinds reside in `LIBRARY_MAPPER`), in the "Mapping" section.
  - [x] In the package-structure document, "Where each class kind goes": the `mapper` package row names the shared config and mapping control; add a row for the new rule; add a row "Every `@Mapper` sets `config = SharedMapperConfig.class`" with `TAP-M1`. In "Alternatives considered", keep the `uses` bullet and say the shared config carries none.
- **Tests**: `ArchitectureTest` passes on today's code and with WP2's classes.

### WP2: The shared config in the mapper library

- **Status**: done
- **Depends on**: WP1
- **Files**: `artifact/backend/library/mapper/src/main/java/tr/girgin/backend/trading/analysis/platform/library/mapper/SharedMapperConfig.java`, `artifact/backend/library/mapper/src/main/java/tr/girgin/backend/trading/analysis/platform/library/mapper/DeepCloneWithConversions.java`, `artifact/backend/library/mapper/src/main/java/tr/girgin/backend/trading/analysis/platform/library/mapper/DurationToMillisMapper.java`, `artifact/backend/library/mapper/src/main/java/tr/girgin/backend/trading/analysis/platform/library/mapper/EnumToLowerCaseNameMapper.java`, `artifact/backend/library/mapper/build.gradle.kts`, `artifact/backend/library/mapper/src/test/java/tr/girgin/backend/trading/analysis/platform/library/mapper/SharedMapperConfigTest.java`, `artifact/backend/library/mapper/README.md`
- **Steps**:
  - [x] Add `DeepCloneWithConversions` and `SharedMapperConfig` as in the design.
  - [x] Put `config = SharedMapperConfig.class` on the two library mappers.
  - [x] `build.gradle.kts`: `testAnnotationProcessor(libs.mapstruct.processor)`.
  - [x] `SharedMapperConfigTest`, with a test-only mapper nested in it (made-up names, no domain, e.g. `SampleToSampleMapper` over a mutable `Sample` holding a mutable `Part` and a `List<Part>`, and a `BigDecimal` mapped to `String`), using the shared config.
  - [x] README: the table lists `SharedMapperConfig` and `DeepCloneWithConversions` ("used by every mapper"); "Only MapStruct mappers live here" becomes mappers, the shared config and its mapping control; a decision "One configuration for every mapper" (deep copy, both unmapped policies at `ERROR`, why `DeepCloneWithConversions` and not `DeepClone`, no `uses`, a source property left out on purpose is listed in `@BeanMapping(ignoreUnmappedSourceProperties = ...)`, enforced by `TAP-M1`).
- **Tests**: `SharedMapperConfigTest` proves the copy is equal to the source but its nested `Part`, its list and the list's elements are not the same instances (`isNotSameAs`), and that the built-in conversion still applies. Existing `DurationToMillisMapperTest`, `EnumToLowerCaseNameMapperTest` pass.

### WP3: BFF mappers, part 1

- **Status**: done
- **Depends on**: WP2
- **Files**: in artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/mapper: `AnalysisDefaultsToAnalysisDefaultsDtoMapper.java`, `AnalysisIdToStringMapper.java`, `AnalysisPreviewSummaryToAnalysisPreviewSummaryDtoMapper.java`, `AnalysisPreviewToAnalysisPreviewDtoMapper.java`, `AnalysisScopeEnumDtoToAnalysisScopeMapper.java`, `AnalysisStatusEnumDtoToAnalysisStatusMapper.java`, `AnalysisToAnalysisDtoMapper.java`, `CatalogToCatalogDtoMapper.java`
- **Steps**:
  - [x] Put the shared config on each mapper.
- **Tests**: the existing `bff:impl` tests pass (mapper tests and delegate tests).

### WP4: BFF mappers, part 2 (exception mappers included)

- **Status**: done
- **Depends on**: WP2
- **Files**: in artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/mapper: `error/AnalysisExceptionToApiExceptionMapper.java`, `error/CatalogExceptionToApiExceptionMapper.java`, `error/IdentityExceptionToApiExceptionMapper.java`, `error/SettingsExceptionToApiExceptionMapper.java`, `ImportResultToImportResultDtoMapper.java`, `PermissionEnumDtoToPermissionMapper.java`, `PermissionToPermissionEnumDtoMapper.java`, `PresetToPresetDtoMapper.java`
- **Steps**:
  - [x] Put the shared config on each mapper.
- **Tests**: the `*ExceptionToApiExceptionMapperTest`s, `DomainExceptionHandlerTest`, `PresetToPresetDtoMapperTest` pass.

### WP5: BFF mappers, part 3

- **Status**: done
- **Depends on**: WP2
- **Files**: in artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/mapper: `PresetValuesDtoToJsonStringMapper.java`, `PriceHistoryToPriceHistoryDtoMapper.java`, `ReportToAnalysisReportDtoMapper.java`, `RoleSummaryToRoleDtoMapper.java`, `RunEventToRunEventDtoMapper.java`, `RunStatsToRunStatsDtoMapper.java`, `SecretStatusToSecretStatusDtoMapper.java`, `StartAnalysisRequestToRequestedAnalysisMapper.java`
- **Steps**:
  - [x] Put the shared config on each mapper.
  - [x] `RunEventToRunEventDtoMapper`: `@BeanMapping(ignoreUnmappedSourceProperties = {"rating", "outcome", "stats", "error"})` on `map`.
- **Tests**: `RoleSummaryToRoleDtoMapperTest`, `PresetToPresetDtoMapperTest` and the delegate tests pass.

### WP6: BFF mappers, part 4

- **Status**: done
- **Depends on**: WP2
- **Files**: in artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/mapper: `StringToAnalysisIdMapper.java`, `SystemHealthToSystemHealthDtoMapper.java`, `UserAccountToUserDtoMapper.java`, `VisibilityEnumDtoToVisibilityMapper.java`, `VisibilityToVisibilityEnumDtoMapper.java`
- **Steps**:
  - [x] Put the shared config on each mapper.
- **Tests**: the existing `bff:impl` tests pass.

### WP7: Analysis adapter, event line mappers

- **Status**: done
- **Depends on**: WP2
- **Files**: in artifact/backend/domain/analysis/adapter/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/analysis/adapter/eventline/mapper: `RunnerOutputLineToRunEventMapper.java`, `RunnerOutputLineToRunStatsMapper.java`, `StringToInstantMapper.java`, `StringToRatingMapper.java`, `StringToRunEventTypeMapper.java`, `StringToRunOutcomeMapper.java`
- **Steps**:
  - [x] Put the shared config on each mapper.
- **Tests**: the event line parser and event store tests of `domain:analysis:adapter` pass.

### WP8: Analysis adapter, runner and run stats mappers

- **Status**: done
- **Depends on**: WP2
- **Files**: in artifact/backend/domain/analysis/adapter/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/analysis/adapter: `runner/mapper/AnalysisSpecToRunnerSpecMapper.java`, `runner/mapper/AnalystToStringMapper.java`, `runner/mapper/AssetTypeToStringMapper.java`, `persistence/mapper/RunStatsEmbeddableToRunStatsMapper.java`, `persistence/mapper/RunStatsToRunStatsEmbeddableMapper.java`
- **Steps**:
  - [x] Put the shared config on each mapper.
- **Tests**: the runner adapter tests (spec written for the runner) and the persistence adapter tests pass.

### WP9: Analysis adapter, persistence mappers

- **Status**: done
- **Depends on**: WP2
- **Files**: in artifact/backend/domain/analysis/adapter/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/analysis/adapter/persistence/mapper: `AnalysisEntityToAnalysisMapper.java`, `AnalysisIdEmbeddableToAnalysisIdMapper.java`, `AnalysisIdToAnalysisIdEmbeddableMapper.java`, `AnalysisSpecEmbeddableToAnalysisSpecMapper.java`, `AnalysisSpecToAnalysisSpecEmbeddableMapper.java`, `AnalysisToAnalysisEntityMapper.java`, `MillisToDurationMapper.java`
- **Steps**:
  - [x] Put the shared config on each mapper.
  - [x] `AnalysisEntityToAnalysisMapper`: `@BeanMapping(ignoreUnmappedSourceProperties = "new")` on `map`.
- **Tests**: the JPA repository adapter tests of `domain:analysis:adapter` pass (Testcontainers).

### WP10: Catalog adapter mappers

- **Status**: done
- **Depends on**: WP2
- **Files**: `artifact/backend/domain/catalog/adapter/build.gradle.kts`; in artifact/backend/domain/catalog/adapter/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/catalog/adapter/runner/mapper: `CatalogJsonToCatalogMapper.java`, `VersionJsonToEngineVersionMapper.java`
- **Steps**:
  - [x] `implementation(project(":backend:library:mapper"))`.
  - [x] Put the shared config on each mapper.
- **Tests**: `TaRunnerEngineInfoAdapterTest`, `DockerEngineInfoAdapterTest` pass; the dependency analysis (`mise run check`) accepts the new dependency.

### WP11: Settings adapter mappers

- **Depends on**: WP2
- **Files**: `artifact/backend/domain/settings/adapter/build.gradle.kts`; in artifact/backend/domain/settings/adapter/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/settings/adapter/persistence/mapper: `AnalysisDefaultsEntityToAnalysisDefaultsMapper.java`, `AnalysisDefaultsToAnalysisDefaultsEntityMapper.java`, `PresetEntityToPresetMapper.java`, `PresetIdEmbeddableToPresetIdMapper.java`, `PresetIdToPresetIdEmbeddableMapper.java`, `PresetToPresetEntityMapper.java`
- **Steps**:
  - [ ] `implementation(project(":backend:library:mapper"))`.
  - [ ] Put the shared config on each mapper.
  - [ ] `AnalysisDefaultsEntityToAnalysisDefaultsMapper`: `@BeanMapping(ignoreUnmappedSourceProperties = "id")`; `PresetToPresetEntityMapper`: `@BeanMapping(ignoreUnmappedSourceProperties = "updatedAt")`.
- **Tests**: the settings persistence adapter tests pass (Testcontainers).

### WP12: Identity adapter, role and timestamp mappers

- **Depends on**: WP2
- **Files**: `artifact/backend/domain/identity/adapter/build.gradle.kts`; in artifact/backend/domain/identity/adapter/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/identity/adapter/persistence/mapper: `InstantToOptionalInstantMapper.java`, `OptionalInstantToInstantMapper.java`, `RoleEntityToRoleMapper.java`, `RoleIdEmbeddableToRoleIdMapper.java`, `RoleIdToRoleIdEmbeddableMapper.java`, `RoleToRoleEntityMapper.java`
- **Steps**:
  - [ ] `implementation(project(":backend:library:mapper"))`.
  - [ ] Put the shared config on each mapper.
- **Tests**: the identity persistence adapter tests pass (Testcontainers).

### WP13: Identity adapter, user mappers

- **Depends on**: WP12
- **Files**: in artifact/backend/domain/identity/adapter/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/identity/adapter/persistence/mapper: `UserEntityToUserMapper.java`, `UserIdEmbeddableToUserIdMapper.java`, `UserIdToUserIdEmbeddableMapper.java`, `UserToUserEntityMapper.java`
- **Steps**:
  - [ ] Put the shared config on each mapper.
  - [ ] `UserToUserEntityMapper`: `@BeanMapping(ignoreUnmappedSourceProperties = {"createdAt", "updatedAt"})`.
- **Tests**: the identity persistence adapter tests pass (Testcontainers).

### WP14: Custom check `RequiredAnnotationAttributeCheck`

- **Depends on**: none
- **Files**: `build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/RequiredAnnotationAttributeCheck.java`, `build-logic/checkstyle-rules/src/main/resources/tr/girgin/checkstyle/messages.properties`, `build-logic/checkstyle-rules/src/test/java/tr/girgin/checkstyle/RequiredAnnotationAttributeCheckTest.java`, `build-logic/checkstyle-rules/src/test/resources/checks/RequiredAnnotationAttributeCheck/Basic.java`, `build-logic/checkstyle-rules/src/test/resources/checks/RequiredAnnotationAttributeCheck/Qualified.java`, `build-logic/checkstyle-rules/docs/RequiredAnnotationAttributeCheck.md`, `build-logic/checkstyle-rules/README.md`
- **Steps**:
  - [ ] Write the check as in the design, following the module's contract.
  - [ ] Add the message key.
  - [ ] Fixtures: `Basic.java` (marker annotation, other attributes only, the attribute set, several required attributes with one missing, the single-element form as `value`, an unrelated annotation, on a class, a method and a parameter); `Qualified.java` (qualified parameter matched through an explicit import, an on-demand import and the qualified annotation in the source; a same-named annotation from another import not reported).
  - [ ] Reference document from `docs/_template.md` (what it matches, every parameter, two configuration examples, the message) and a row in the README's check table.
- **Tests**: `RequiredAnnotationAttributeCheckTest` (each fixture reports exactly its marked lines; missing and invalid `annotations` / `attributes` throw; message with and without `suggestion`); `DocumentationTest`; the module's 100% coverage gate (`mise run check` runs `checkstyleRulesCheck`).

### WP15: Rule `TAP-M1`, compiler arguments, docs, version

- **Depends on**: WP3, WP4, WP5, WP6, WP7, WP8, WP9, WP10, WP11, WP13, WP14
- **Files**: `config/checkstyle/checkstyle.xml`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-M1/Violation.java`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-M1/Compliant.java`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-M1/Suppressed.java`, `build-logic/src/main/kotlin/tradinganalysisplatform.mapstruct.gradle.kts`, `docs/coding-convention/backend-java-checkstyle.md`
- **Steps**:
  - [ ] Add the `TAP-M1` module at the end of the project rules in `checkstyle.xml` as in the design.
  - [ ] Fixtures: `Violation.java` (`@Mapper` and `@Mapper(uses = ...)` without `config`, marked), `Compliant.java` (`@Mapper(config = SharedMapperConfig.class)`, also with `uses`; an unrelated `Mapper` annotation), `Suppressed.java` (`@SuppressWarnings("checkstyle:TAP-M1")` with a reason).
  - [ ] Remove `defaultComponentModel`, `defaultInjectionStrategy` and `unmappedTargetPolicy` from the plugin's compiler arguments; keep `suppressGeneratorTimestamp`.
  - [ ] Checkstyle convention: add `M` (MapStruct) to the id groups; a catalogue row for `TAP-M1` (main and test, `RequiredAnnotationAttributeCheck`, `annotations = org.mapstruct.Mapper`, `attributes = config`, reason: one place sets deep copying and the unmapped policies, a mapper without it silently shares state and drops fields; bad `@Mapper(uses = X.class)` / good `@Mapper(config = SharedMapperConfig.class, uses = X.class)`).
  - [ ] `mise run version:bump minor`.
- **Tests**: `ProjectRulesTest` runs `TAP-M1` on its fixtures; `DocumentationTest` finds the catalogue row; `mise run build` passes with no `TAP-M1` finding and with the compiler arguments gone (the generated mappers are still Spring beans with constructor injection, so the application context and every integration test still start).

## Tests

- Unit: `SharedMapperConfigTest` proves the deep copy and the built-in conversion with the shared
  config; `RequiredAnnotationAttributeCheckTest` proves the check.
- Rule: `ProjectRulesTest` proves `TAP-M1` reports a `@Mapper` without `config` and nothing else.
- Compile: the unmapped source and target policies are proved by the compile of every module
  (MapStruct reports at `ERROR`); the trial run during planning listed the five mappers that need
  `@BeanMapping(ignoreUnmappedSourceProperties = ...)`.
- Architecture: `ArchitectureTest` (`mapper_packages_hold_only_mappers`, the new
  `mapper_configs_live_in_the_mapper_library`).
- Regression: every existing mapper, adapter, delegate and integration test (`mise run build`); the
  e2e tests do not change.
- The issue is done when `mise run build` is green and a `@Mapper` without `config` fails it
  (`TAP-M1` fixtures).

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `artifact/backend/library/mapper/README.md` | the shared config and mapping control in "What it holds"; what the package holds; the decision "One configuration for every mapper" (WP2) |
| Text | `docs/coding-convention/backend-java-package-structure.md`, "Where each class kind goes" and "Shared libraries" | the config's place, the new ArchUnit rule, `TAP-M1`; the shared config has no `uses` (WP1) |
| Text | `docs/coding-convention/backend-java-checkstyle.md`, "Project rules" | the `M` group, the `TAP-M1` row in the rules catalogue (WP15) |
| Text | `build-logic/checkstyle-rules/README.md`, `build-logic/checkstyle-rules/docs/RequiredAnnotationAttributeCheck.md` | the new check's row and reference document (WP14) |
| Screenshot | none | no page changes |

## Out of scope

- `typeConversionPolicy = ERROR` (lossy conversions such as `BigDecimal` to `Double` in
  `RunStatsToRunStatsEmbeddableMapper`): a separate decision about the stored cost type; an issue if
  wanted.
- Checking which config a mapper names: `TAP-M1` checks that `config` is set; the ArchUnit rule
  keeps every `@MapperConfig` in the mapper library, of which `SharedMapperConfig` is the only one.

## Open questions

None.
