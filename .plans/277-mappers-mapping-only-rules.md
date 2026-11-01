# Plan: Mappers hold mapping only: no @Context, no expression

- **Issue**: #277 (Mappers hold mapping only: forbid @Context and expression in mappers)
- **Plan**: 1 of 2 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

No mapper takes `@Context` parameters or Java code in a string any more, and the build fails when one
does: `TAP-M2` forbids `org.mapstruct.Context`, `TAP-M3` forbids the `expression`,
`defaultExpression` and `conditionExpression` attributes of `@Mapping`, both in main sources. The
analysis DTO is mapped from one self-contained source object that already carries the caller's
rights and the owner's username. The REST API does not change. Plan 2
(`277-hand-written-mappers`) handles the `default`-only mappers (work item 3 of the issue).

## Affected parts and their conventions

- Backend: [package structure](../docs/coding-convention/backend-java-package-structure.md),
  [Checkstyle](../docs/coding-convention/backend-java-checkstyle.md),
  [shared mapper library](../artifact/backend/library/mapper/README.md),
  [REST API](../docs/coding-convention/backend-java-rest-api.md) (unchanged endpoints).
- Custom Checkstyle checks: [module README](../build-logic/checkstyle-rules/README.md) (contract,
  tests, adding a check and a project rule), [doc template](../build-logic/checkstyle-rules/docs/_template.md).

Paths below are under `artifact/backend/` unless they start with `build-logic/` or `config/`; `…`
is the module's package path.

## Design

- **Rights carry `canChangeVisibility`.** `AnalysisRights` gets the component
  `canChangeVisibility` (between `canManage` and `allowedVisibilities`), set by
  `AnalysisAccessService.rights` to "`allowedVisibilities` is not empty". A computed value belongs to
  the service that builds the source, not to the mapper, and a record may hold no derived method
  (`models_are_data`).
- **The source object: `AnalysisView`.** A new orchestration feature `analysisview` (the data comes
  from two domains, analysis and identity, and the BFF has no package for model types):
  - `orchestration.analysisview.model.AnalysisView(Analysis analysis, AnalysisOwner owner, AnalysisRights rights)`;
  - `orchestration.analysisview.model.AnalysisOwner(OwnerId id, Username username)`: `owner` is null
    when the analysis has none, `username` is null when the user no longer exists (today's
    `AnalysisOwnerDto` behaviour);
  - `orchestration.analysisview.inbound.ViewAnalysesUseCase` (`@Service`), with
    `AnalysisView view(Analysis, AnalysisAccess)` and `List<AnalysisView> view(List<Analysis>, AnalysisAccess)`:
    rights per analysis from `GetAnalysisRightsUseCase`, usernames with one
    `UserDirectoryUseCase.usernames` call per list (the code moves from
    `AnalysesApiDelegateImpl.usernames`).
- **The mapper.** `AnalysisToAnalysisDtoMapper` becomes `AnalysisViewToAnalysisDtoMapper` (named after
  its new source). It maps `analysis` onto the DTO with `@Mapping(target = ".", source = "analysis")`,
  `canManage`, `canChangeVisibility` and `allowedVisibilities` from `rights.*`, and `owner` with an
  abstract `AnalysisOwnerDto map(AnalysisOwner)` (`id` from `id.value`, `username` from
  `username.value`); the explicit `owner` mapping takes the place of `analysis.owner`. No `@Context`,
  no `default` method. `AnalysesApiDelegateImpl` calls `ViewAnalysesUseCase` and the mapper, and no
  longer injects `GetAnalysisRightsUseCase` and `UserDirectoryUseCase`.
- **`RoleSummaryToRoleDtoMapper`.** `permissions` maps from `role.permissions` through a typed method
  `default List<PermissionEnumDto> map(Set<Permission>)` that sorts by key and calls the abstract
  `map(List<Permission>)`; it replaces `List<Permission> map(Set<Permission>)` (same parameter
  type). `uses` stays as it is, so `RoleSummaryToRoleDtoMapperTest` compiles and passes unchanged.
- **`CatalogJsonToCatalogMapper`.** The five `defaultExpression`s go; the mapper sets
  `nullValueIterableMappingStrategy = NullValueIterableMappingStrategy.RETURN_DEFAULT`, on this mapper
  only (a change in `SharedMapperConfig` would change every mapper's null lists). If the generated
  code still passes a null `assetTypes` through (a `List<String>` copy), a typed
  `List<String> map(List<String>)` method in this mapper covers it; the new mapper test decides.
- **One generic check: `ForbiddenAnnotationCheck`** (`tr.girgin.checkstyle`). Parameters
  `annotations` (required, simple or qualified, as in `RequiredAnnotationAttributeCheck`),
  `attributes` (optional) and `suggestion`. Without `attributes` every use of a listed annotation is
  reported; with `attributes`, only a use that sets one of them, once per attribute. Nested
  annotations (`@Mapping` inside `@Mappings({...})`) are annotations of their own, so they are
  checked too. The attribute names of an annotation (today the private
  `RequiredAnnotationAttributeCheck.setAttributes`) move to `support/AnnotationAttributes`, since two
  checks need them. An existing check does not fit: `IllegalImport` misses a qualified
  `@org.mapstruct.Context`, and a `RegexpSinglelineJava` on `expression =` would also match a local
  variable of that name.
- **The rules.** `TAP-M2`: `annotations = org.mapstruct.Context`. `TAP-M3`:
  `annotations = org.mapstruct.Mapping`, `attributes = expression, defaultExpression, conditionExpression`
  (`conditionExpression` is Java in a string as well; `constant` and `defaultValue` are literals and
  stay allowed). Both main sources only, with a `SuppressionSingleFilter` like `TAP-D7`'s.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: Rights carry canChangeVisibility

- **Status**: open
- **Depends on**: none
- **Files**: `domain/analysis/core/src/main/java/…/inbound/AnalysisRights.java`, `domain/analysis/core/src/main/java/…/service/AnalysisAccessService.java`, `domain/analysis/core/src/test/java/…/service/AnalysisAccessServiceTest.java`
- **Steps**:
  - [ ] Add the component and set it in `rights`.
- **Tests**: `AnalysisAccessServiceTest`'s rights cases expect `canChangeVisibility` true exactly where
  `allowedVisibilities` is not empty.

### WP2: ViewAnalysesUseCase

- **Status**: open
- **Depends on**: WP1
- **Files**: `orchestration/src/main/java/…/analysisview/model/AnalysisView.java`, `orchestration/src/main/java/…/analysisview/model/AnalysisOwner.java`, `orchestration/src/main/java/…/analysisview/inbound/ViewAnalysesUseCase.java`, `orchestration/src/test/java/…/analysisview/inbound/ViewAnalysesUseCaseTest.java`
- **Steps**:
  - [ ] Write the model types and the use case as in "Design".
- **Tests**: `ViewAnalysesUseCaseTest`: rights per analysis (two analyses with different rights in
  one list), the owner's username, an owner without a user (`username` null), an analysis without
  owner (`owner` null), one directory lookup for a whole list.

### WP3: Analysis DTO mapped from AnalysisView

- **Status**: open
- **Depends on**: WP2
- **Files**: `bff/impl/src/main/java/…/mapper/AnalysisToAnalysisDtoMapper.java` (deleted), `bff/impl/src/main/java/…/mapper/AnalysisViewToAnalysisDtoMapper.java`, `bff/impl/src/main/java/…/AnalysesApiDelegateImpl.java`, `bff/impl/src/test/java/…/mapper/AnalysisViewToAnalysisDtoMapperTest.java`
- **Steps**:
  - [ ] Replace the mapper and switch every `dto(...)` and the list in the delegate to
        `ViewAnalysesUseCase`; drop the delegate's `usernames` helper.
  - [ ] Keep the delegate's `ParameterNumber` / `ClassFanOutComplexity` suppressions only while they
        are still needed.
- **Tests**: `AnalysisViewToAnalysisDtoMapperTest` (built like `RoleSummaryToRoleDtoMapperTest`, with
  the generated `*Impl`s): every analysis field, `canManage`, `canChangeVisibility`,
  `allowedVisibilities` in order, owner with and without username, no owner.
  `AnalysisSharingIntegrationTest` and `AnalysesApiIntegrationTest` pass unchanged.

### WP4: RoleSummaryToRoleDtoMapper without expression

- **Status**: open
- **Depends on**: none
- **Files**: `bff/impl/src/main/java/…/mapper/RoleSummaryToRoleDtoMapper.java`
- **Steps**:
  - [ ] Replace the `expression` with the typed method.
- **Tests**: `RoleSummaryToRoleDtoMapperTest` passes unchanged (permissions sorted by key).

### WP5: CatalogJsonToCatalogMapper without defaultExpression

- **Status**: open
- **Depends on**: none
- **Files**: `domain/catalog/adapter/src/main/java/…/runner/mapper/CatalogJsonToCatalogMapper.java`, `domain/catalog/adapter/src/test/java/…/runner/mapper/CatalogJsonToCatalogMapperTest.java`
- **Steps**:
  - [ ] Replace the `defaultExpression`s with the null-value strategy.
- **Tests**: new `CatalogJsonToCatalogMapperTest`: a catalog JSON without `providers`, `analysts`,
  `assetTypes` and a provider without `quick_models` / `deep_models` map to empty lists, a given list
  is copied; `TaRunnerEngineInfoAdapterTest` and `DockerEngineInfoAdapterTest` pass unchanged.

### WP6: Shared annotation attribute helper

- **Status**: open
- **Depends on**: none
- **Files**: `build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/support/AnnotationAttributes.java`, `build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/RequiredAnnotationAttributeCheck.java`
- **Steps**:
  - [ ] Move the attribute name collection into the helper and use it from the check.
- **Tests**: `RequiredAnnotationAttributeCheckTest` passes unchanged; coverage stays at 100%.

### WP7: ForbiddenAnnotationCheck

- **Status**: open
- **Depends on**: WP6
- **Files**: `build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/ForbiddenAnnotationCheck.java`, `build-logic/checkstyle-rules/src/main/resources/tr/girgin/checkstyle/messages.properties`, `build-logic/checkstyle-rules/src/test/java/tr/girgin/checkstyle/ForbiddenAnnotationCheckTest.java`, `build-logic/checkstyle-rules/src/test/resources/checks/ForbiddenAnnotationCheck/Basic.java`, `build-logic/checkstyle-rules/src/test/resources/checks/ForbiddenAnnotationCheck/Attributes.java`, `build-logic/checkstyle-rules/src/test/resources/checks/ForbiddenAnnotationCheck/Qualified.java`, `build-logic/checkstyle-rules/src/test/resources/checks/ForbiddenAnnotationCheck/OtherImport.java`
- **Steps**:
  - [ ] Two message keys: the annotation itself, and an attribute it sets.
- **Tests**: `ForbiddenAnnotationCheckTest` with the fixtures: a forbidden annotation on a parameter,
  a method and a type; attributes set and not set; `@Mapping` nested in `@Mappings`; simple, qualified,
  on-demand-imported and same-package names; an unrelated annotation of the same simple name not
  reported; invalid and missing parameters; the message with and without `suggestion`.
  `DocumentationTest` passes with the new reference document; coverage stays at 100%.

### WP8: TAP-M2

- **Status**: open
- **Depends on**: WP3, WP7
- **Files**: `config/checkstyle/checkstyle.xml`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-M2/Violation.java`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-M2/Compliant.java`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-M2/Suppressed.java`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-M2/sources`
- **Steps**:
  - [ ] Add the rule and its main-only filter after `TAP-M1`.
- **Tests**: `ProjectRulesTest` runs the `TAP-M2` fixtures (`sources` = `main`); `checkstyleMain`
  passes on the backend.

### WP9: TAP-M3

- **Status**: open
- **Depends on**: WP3, WP4, WP5, WP8
- **Files**: `config/checkstyle/checkstyle.xml`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-M3/Violation.java`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-M3/Compliant.java`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-M3/Suppressed.java`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-M3/sources`
- **Steps**:
  - [ ] Add the rule and its main-only filter after `TAP-M2`.
- **Tests**: `ProjectRulesTest` runs the `TAP-M3` fixtures (each of the three attributes, on a single
  `@Mapping` and inside `@Mappings`; `Compliant.java` uses `source`, `constant`, `defaultValue`,
  `qualifiedByName`); `checkstyleMain` passes on the backend.

## Tests

Each work package's tests. The issue's "forbidden by the build" is proved by `ProjectRulesTest`
(WP8, WP9) and by `mise run build` passing with no `@Context` and no `expression` left in main code.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-checkstyle.md`, rules catalogue | Rows `TAP-M2` and `TAP-M3` (reason: the data comes with the source object; Java in a string is not compiled until MapStruct generates the code); the list of settings that limit where a check applies gets both (and its count corrected) |
| Text | `docs/coding-convention/backend-java-package-structure.md`, "Where each class kind goes" (mapper rows) | A row "a mapper maps only": no `@Context` (the delegate or a use case puts the data into the source object), no expression attributes; a computed value comes from the service or use case that builds the source, a nested value through `source = "a.b"` or a typed method, an empty list from `nullValueIterableMappingStrategy`; rules `TAP-M2`, `TAP-M3` |
| Text | `build-logic/checkstyle-rules/docs/ForbiddenAnnotationCheck.md` (new, from the template) | What it matches, parameters, examples for both forms (annotation, attributes), message, project rules |
| Text | `build-logic/checkstyle-rules/README.md`, "The checks" | Row for `ForbiddenAnnotationCheck` (`TAP-M2`, `TAP-M3`) |
| Screenshot | none | No page changes |

## Out of scope

- Mappers that hold only `default` methods (`StringToAnalysisIdMapper` and the hand-written DTO
  mappers) and the ArchUnit rule for them: plan 2, `277-hand-written-mappers`.
- `openapi.json` and the frontend API types: the REST API does not change.

## Open questions

- `AnalysisView` lives in a new orchestration feature `analysisview` because its data spans two
  domains and `bff.delegate.impl` has no model package. The alternative is a model package in the
  BFF (`bff.delegate.impl.model`), which would be a new kind of package with its own ArchUnit rule.
  The plan takes the orchestration feature; is that right?
- `TAP-M3` also forbids `conditionExpression`, which the issue does not name (nothing uses it). Keep
  it in the rule?
