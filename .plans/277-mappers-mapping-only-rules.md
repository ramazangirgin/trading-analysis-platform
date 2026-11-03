# Plan: Mappers hold mapping only: no @Context, no expression

- **Issue**: #277 (Mappers hold mapping only: forbid @Context and expression in mappers)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

Mappers only map, and the build fails when one does more:
- no mapper takes `@Context` parameters (`TAP-M2`);
- no `@Mapping` holds Java code in a string: `TAP-M3` forbids `expression`, `defaultExpression` and
  `conditionExpression`, both rules in main sources;
- a mapper with only `default` methods (a hand-written converter) exists only because another mapper
  uses it (an ArchUnit rule).

The analysis DTO is mapped from one self-contained source object that already carries the caller's
rights and the owner's username. `StringToAnalysisIdMapper` is gone, and the four hand-written DTO
mappers that a delegate injects become MapStruct mappings. The REST API and its JSON do not change
(field order, key spelling and list order included).

## Affected parts and their conventions

- Backend: [package structure](../docs/coding-convention/backend-java-package-structure.md),
  [Checkstyle](../docs/coding-convention/backend-java-checkstyle.md),
  [shared mapper library](../artifact/backend/library/mapper/README.md),
  [REST API](../docs/coding-convention/backend-java-rest-api.md) (the endpoints stay as they are),
  [Changing a convention](../docs/coding-convention/README.md#changing-a-convention) (a new rule must
  be shown to fail).
- Custom Checkstyle checks: [module README](../build-logic/checkstyle-rules/README.md) (the contract,
  the tests, how to add a check and a project rule), [doc template](../build-logic/checkstyle-rules/docs/_template.md).

Paths below are under `artifact/backend/` unless they start with `build-logic/` or `config/`; `…`
is the module's package path.

## Design

### @Context and expression

- **Rights carry `canChangeVisibility`.** `AnalysisRights` gets the component
  `canChangeVisibility`, between `canManage` and `allowedVisibilities`.
  `AnalysisAccessService.rights` sets it to "`allowedVisibilities` is not empty". A computed value
  belongs to the service that builds the source, not to the mapper. A record may not hold a derived
  method either (`models_are_data`).
- **The source object is `AnalysisView`**, in a new orchestration feature `analysisview`. Its data
  comes from two domains, analysis and identity, and the BFF has no package for model types.
  - `orchestration.analysisview.model.AnalysisView(Analysis analysis, AnalysisOwner owner, AnalysisRights rights)`.
  - `orchestration.analysisview.model.AnalysisOwner(OwnerId id, Username username)`. `owner` is null
    when the analysis has none, and `username` is null when the user no longer exists, as
    `AnalysisOwnerDto` behaves today.
  - `orchestration.analysisview.inbound.ViewAnalysesUseCase` (`@Service`), with
    `AnalysisView view(Analysis, AnalysisAccess)` and
    `List<AnalysisView> view(List<Analysis>, AnalysisAccess)`. It gets the rights of each analysis
    from `GetAnalysisRightsUseCase`, and the usernames with one `UserDirectoryUseCase.usernames` call
    per list. That code moves here from `AnalysesApiDelegateImpl.usernames`.
- **The analysis mapper.** `AnalysisToAnalysisDtoMapper` becomes `AnalysisViewToAnalysisDtoMapper`,
  named after its new source.
  - It maps `analysis` onto the DTO with `@Mapping(target = ".", source = "analysis")`.
  - `canManage`, `canChangeVisibility` and `allowedVisibilities` come from `rights.*`.
  - `owner` comes from an abstract `AnalysisOwnerDto map(AnalysisOwner)` (`id` from `id.value`,
    `username` from `username.value`). The explicit `owner` mapping replaces `analysis.owner`.
  - It has no `@Context` and no `default` method.
  - `AnalysesApiDelegateImpl` calls `ViewAnalysesUseCase` and then the mapper. It no longer injects
    `GetAnalysisRightsUseCase` and `UserDirectoryUseCase`.
- **`RoleSummaryToRoleDtoMapper`.** `permissions` maps from `role.permissions` through a typed method,
  `default List<PermissionEnumDto> map(Set<Permission>)`, which sorts by key and calls the abstract
  `map(List<Permission>)`. It replaces `List<Permission> map(Set<Permission>)`, which takes the same
  parameter type. `uses` stays as it is, so `RoleSummaryToRoleDtoMapperTest` compiles and passes
  unchanged.
- **`CatalogJsonToCatalogMapper`.** The five `defaultExpression`s go. The mapper sets
  `nullValueIterableMappingStrategy = NullValueIterableMappingStrategy.RETURN_DEFAULT`, on this mapper
  only: setting it in `SharedMapperConfig` would change the null lists of every mapper. If the
  generated code still passes a null `assetTypes` through (a `List<String>` copy), a typed
  `List<String> map(List<String>)` in this mapper covers it. The new mapper test shows which case
  applies.
- **One generic check, `ForbiddenAnnotationCheck`** (`tr.girgin.checkstyle`).
  - Parameters: `annotations` (required; simple or qualified names, as in
    `RequiredAnnotationAttributeCheck`), `attributes` (optional) and `suggestion`.
  - Without `attributes`, every use of a listed annotation is reported. With `attributes`, only a use
    that sets one of them is reported, once per attribute.
  - A nested annotation (`@Mapping` inside `@Mappings({...})`) is an annotation of its own, so it is
    checked too.
  - `RequiredAnnotationAttributeCheck` has a private `setAttributes` that reads the attribute names
    of an annotation. It moves to `support/AnnotationAttributes`, since two checks need it.
  - No existing check fits: `IllegalImport` misses a qualified `@org.mapstruct.Context`, and a
    `RegexpSinglelineJava` on `expression =` would also match a local variable of that name.
- **The rules.**
  - `TAP-M2`: `annotations = org.mapstruct.Context`.
  - `TAP-M3`: `annotations = org.mapstruct.Mapping` and
    `attributes = expression, defaultExpression, conditionExpression`. `conditionExpression` is Java in
    a string as well. `constant` and `defaultValue` are literals and stay allowed.
  - Both apply to main sources only, through a `SuppressionSingleFilter` like the one for `TAP-D7`.

### Hand-written mappers

How today's `default`-only mappers (a `@Mapper` with no abstract method) are sorted:

| Mapper | Users | Result |
|---|---|---|
| `AnalysisIdToStringMapper`, `AssetTypeToStringMapper`, `AnalystToStringMapper`, `StringToInstantMapper`, `StringToRunOutcomeMapper`, `StringToRatingMapper`, `StringToRunEventTypeMapper`, `RunnerOutputLineToRunStatsMapper`, `InstantToOptionalInstantMapper`, `OptionalInstantToInstantMapper`, `DurationToMillisMapper`, `EnumToLowerCaseNameMapper` | another mapper's `uses` | kept |
| `StringToAnalysisIdMapper` | injected into `AnalysesApiDelegateImpl` and `PublicAnalysesApiDelegateImpl` only | removed; the delegates write `new AnalysisId(id)` |
| `*ExceptionToApiExceptionMapper` (`mapper.error`) | `DomainExceptionHandler` only, as the [REST convention](../docs/coding-convention/backend-java-rest-api.md#errors) requires | kept, and exempt from the rule |
| `UserAccountToUserDtoMapper`, `PriceHistoryToPriceHistoryDtoMapper`, `ReportToAnalysisReportDtoMapper`, `AnalysisPreviewToAnalysisPreviewDtoMapper` | injected into a delegate only | rewritten as abstract MapStruct mappings |

- **The four DTO mappers.** Moving their code into the delegates would put mapping into the
  delegates, so they become declarative mappings: nested sources (`key.ticker`, `id.value`,
  `username.value`), generated list and map methods, and converters in `uses`.
  - `UserAccountToUserDtoMapper`:
    - an abstract `UserRoleDto map(Role)`, with `id` from `id.value` and the other role properties
      listed in `@BeanMapping(ignoreUnmappedSourceProperties = ...)`;
    - `OptionalInstantToInstantMapper` for `lockedUntil` and `lastLoginAt`. That converter now has a
      second module using it, so it moves from the identity adapter to `library.mapper`
      (`mappers_are_not_duplicated_across_modules`).
  - `ReportToAnalysisReportDtoMapper`:
    - the map keys are lower-cased through `EnumToLowerCaseNameMapper`
      (`@MapMapping(keyQualifiedByName = "lowerCaseName")`). If the `sections` and `debates` map
      methods clash by erasure, typed key converters in `uses` take their place:
      `ReportSectionToStringMapper` and `DebateSpeakerToStringMapper`;
    - `sources` are sorted by name in a typed `List<String> map(Set<ReportSource>)`;
    - `rating` goes through MapStruct's enum mapping.
  - `AnalysisPreviewToAnalysisPreviewDtoMapper`:
    - the keys are mapped the same way as in the report mapper;
    - the order of the sections comes from the source. `PreviewCutter` passes its `EnumMap` on
      unmodifiable (`Collections.unmodifiableMap`) instead of through `Map.copyOf`, which loses the
      order, so the mapper's `TreeMap` goes.
  - `PriceHistoryToPriceHistoryDtoMapper`: `ticker` and `tradeDate` come from `key`, and `points`
    are generated.
- **The rule `hand_written_mappers_are_used_by_other_mappers`**, in the "Mapping" section of
  `ArchitectureTest`:
  - It applies to every class annotated `@Mapper` whose methods are all non-abstract, outside
    `bff.delegate.impl.mapper.error`.
  - Such a class must have a dependent class in a `mapper` package other than itself and its own
    generated `*Impl`.
  - ArchUnit sees that dependency because the generated `*Impl` of the using mapper holds the
    converter as a constructor-injected field.
  - A custom `ArchCondition`, like `haveASimpleNameNoOtherMapperHas`, states it.
  - A mapper used through `uses` may still be injected somewhere else too (`JsonlEventStoreAdapter`
    injects `EnumToLowerCaseNameMapper`).

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: Rights carry canChangeVisibility

- **Status**: done
- **Depends on**: none
- **Files**: `domain/analysis/core/src/main/java/…/inbound/AnalysisRights.java`, `domain/analysis/core/src/main/java/…/service/AnalysisAccessService.java`, `domain/analysis/core/src/test/java/…/service/AnalysisAccessServiceTest.java`
- **Steps**:
  - [x] Add the component and set it in `rights`.
- **Tests**: in `AnalysisAccessServiceTest`'s rights cases, `canChangeVisibility` is true exactly
  where `allowedVisibilities` is not empty.

### WP2: ViewAnalysesUseCase

- **Status**: open
- **Depends on**: WP1
- **Files**: `orchestration/src/main/java/…/analysisview/model/AnalysisView.java`, `orchestration/src/main/java/…/analysisview/model/AnalysisOwner.java`, `orchestration/src/main/java/…/analysisview/inbound/ViewAnalysesUseCase.java`, `orchestration/src/test/java/…/analysisview/inbound/ViewAnalysesUseCaseTest.java`
- **Steps**:
  - [ ] Write the model types and the use case.
- **Tests**: `ViewAnalysesUseCaseTest`:
  - one list with two analyses whose rights differ gets the rights of each;
  - the owner's username is set;
  - an owner who is no longer a user has a null `username`;
  - an analysis without an owner has a null `owner`;
  - a whole list needs one directory lookup.

### WP3: Analysis DTO mapped from AnalysisView

- **Status**: open
- **Depends on**: WP2
- **Files**: `bff/impl/src/main/java/…/mapper/AnalysisToAnalysisDtoMapper.java` (deleted), `bff/impl/src/main/java/…/mapper/AnalysisViewToAnalysisDtoMapper.java`, `bff/impl/src/main/java/…/AnalysesApiDelegateImpl.java`, `bff/impl/src/test/java/…/mapper/AnalysisViewToAnalysisDtoMapperTest.java`
- **Steps**:
  - [ ] Replace the mapper. Every `dto(...)` and the list in the delegate go through
        `ViewAnalysesUseCase`, and the delegate's `usernames` helper goes.
  - [ ] Keep the delegate's `ParameterNumber` and `ClassFanOutComplexity` suppressions only if they
        are still needed.
- **Tests**:
  - `AnalysisViewToAnalysisDtoMapperTest`, built with the generated `*Impl`s like
    `RoleSummaryToRoleDtoMapperTest`. It covers every analysis field, `canManage`,
    `canChangeVisibility`, `allowedVisibilities` in order, an owner with and without a username, and
    no owner.
  - `AnalysisSharingIntegrationTest` and `AnalysesApiIntegrationTest` pass unchanged.

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
- **Tests**:
  - A new `CatalogJsonToCatalogMapperTest`: a catalog JSON without `providers`, `analysts` and
    `assetTypes`, and a provider without `quick_models` and `deep_models`, map to empty lists; a
    list that is given is copied.
  - `TaRunnerEngineInfoAdapterTest` and `DockerEngineInfoAdapterTest` pass unchanged.

### WP6: Shared annotation attribute helper

- **Status**: open
- **Depends on**: none
- **Files**: `build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/support/AnnotationAttributes.java`, `build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/RequiredAnnotationAttributeCheck.java`
- **Steps**:
  - [ ] Move the code that collects attribute names into the helper, and use it from the check.
- **Tests**: `RequiredAnnotationAttributeCheckTest` passes unchanged, and coverage stays at 100%.

### WP7: ForbiddenAnnotationCheck

- **Status**: open
- **Depends on**: WP6
- **Files**: `build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/ForbiddenAnnotationCheck.java`, `build-logic/checkstyle-rules/src/main/resources/tr/girgin/checkstyle/messages.properties`, `build-logic/checkstyle-rules/src/test/java/tr/girgin/checkstyle/ForbiddenAnnotationCheckTest.java`, `build-logic/checkstyle-rules/src/test/resources/checks/ForbiddenAnnotationCheck/Basic.java`, `build-logic/checkstyle-rules/src/test/resources/checks/ForbiddenAnnotationCheck/Attributes.java`, `build-logic/checkstyle-rules/src/test/resources/checks/ForbiddenAnnotationCheck/Qualified.java`, `build-logic/checkstyle-rules/src/test/resources/checks/ForbiddenAnnotationCheck/OtherImport.java`
- **Steps**:
  - [ ] Two message keys: one for the annotation itself, one for an attribute it sets.
- **Tests**:
  - `ForbiddenAnnotationCheckTest`, with fixtures for:
    - a forbidden annotation on a parameter, a method and a type;
    - attributes that are set and attributes that are not;
    - `@Mapping` nested in `@Mappings`;
    - simple, qualified, on-demand-imported and same-package names;
    - an unrelated annotation with the same simple name (not reported);
    - invalid and missing parameters;
    - the message with and without `suggestion`.
  - `DocumentationTest` passes with the new reference document, and coverage stays at 100%.

### WP8: TAP-M2

- **Status**: open
- **Depends on**: WP3, WP7
- **Files**: `config/checkstyle/checkstyle.xml`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-M2/Violation.java`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-M2/Compliant.java`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-M2/Suppressed.java`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-M2/sources`
- **Steps**:
  - [ ] Add the rule and its main-only filter after `TAP-M1`.
- **Tests**: `ProjectRulesTest` runs the `TAP-M2` fixtures (`sources` = `main`), and
  `checkstyleMain` passes on the backend.

### WP9: TAP-M3

- **Status**: open
- **Depends on**: WP3, WP4, WP5, WP8
- **Files**: `config/checkstyle/checkstyle.xml`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-M3/Violation.java`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-M3/Compliant.java`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-M3/Suppressed.java`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-M3/sources`
- **Steps**:
  - [ ] Add the rule and its main-only filter after `TAP-M2`.
- **Tests**:
  - `ProjectRulesTest` runs the `TAP-M3` fixtures: each of the three attributes, on a single
    `@Mapping` and inside `@Mappings`. `Compliant.java` uses `source`, `constant`, `defaultValue`
    and `qualifiedByName`.
  - `checkstyleMain` passes on the backend.

### WP10: Remove StringToAnalysisIdMapper

- **Status**: open
- **Depends on**: WP3
- **Files**: `bff/impl/src/main/java/…/mapper/StringToAnalysisIdMapper.java` (deleted), `bff/impl/src/main/java/…/AnalysesApiDelegateImpl.java`, `bff/impl/src/main/java/…/PublicAnalysesApiDelegateImpl.java`
- **Steps**:
  - [ ] Replace each `idMapper.map(id)` with `new AnalysisId(id)`.
- **Tests**: `AnalysesApiIntegrationTest`, `AnalysisSharingIntegrationTest` and the public analyses
  tests pass unchanged; an invalid ID still answers 404.

### WP11: OptionalInstantToInstantMapper into the mapper library

- **Status**: open
- **Depends on**: none
- **Files**: `domain/identity/adapter/src/main/java/…/persistence/mapper/OptionalInstantToInstantMapper.java` (deleted), `library/mapper/src/main/java/…/library/mapper/OptionalInstantToInstantMapper.java`, `domain/identity/adapter/src/main/java/…/persistence/mapper/UserToUserEntityMapper.java`, `library/mapper/src/test/java/…/library/mapper/OptionalInstantToInstantMapperTest.java`
- **Steps**:
  - [ ] Move the class; `UserToUserEntityMapper` imports it from the library.
- **Tests**: `OptionalInstantToInstantMapperTest` (empty maps to null, present maps to its value, no
  domain names); the identity adapter's repository tests pass unchanged.

### WP12: UserAccountToUserDtoMapper as a MapStruct mapping

- **Status**: open
- **Depends on**: WP11
- **Files**: `bff/impl/src/main/java/…/mapper/UserAccountToUserDtoMapper.java`, `bff/impl/src/test/java/…/mapper/UserAccountToUserDtoMapperTest.java`
- **Steps**:
  - [ ] Rewrite the mapper as an abstract MapStruct mapping.
- **Tests**: `UserAccountToUserDtoMapperTest`: every field, the roles in the given order, and
  `lockedUntil` and `lastLoginAt` both empty and present.

### WP13: PriceHistoryToPriceHistoryDtoMapper as a MapStruct mapping

- **Status**: open
- **Depends on**: none
- **Files**: `bff/impl/src/main/java/…/mapper/PriceHistoryToPriceHistoryDtoMapper.java`, `bff/impl/src/test/java/…/mapper/PriceHistoryToPriceHistoryDtoMapperTest.java`
- **Steps**:
  - [ ] Rewrite the mapper as an abstract MapStruct mapping.
- **Tests**: `PriceHistoryToPriceHistoryDtoMapperTest`: the ticker, the trade date, and the points in
  order with all their values.

### WP14: ReportToAnalysisReportDtoMapper as a MapStruct mapping

- **Status**: open
- **Depends on**: none
- **Files**: `bff/impl/src/main/java/…/mapper/ReportToAnalysisReportDtoMapper.java`, `bff/impl/src/test/java/…/mapper/ReportToAnalysisReportDtoMapperTest.java`
- **Steps**:
  - [ ] Rewrite the mapper as an abstract MapStruct mapping. Add the two key converters only if the
        erasure clash needs them.
- **Tests**: `ReportToAnalysisReportDtoMapperTest`: lower-case keys in enum order for sections and
  debates, sources sorted by name, a rating with and without a value, and `modifiedAt`.

### WP15: Preview sections keep their order

- **Status**: open
- **Depends on**: none
- **Files**: `orchestration/src/main/java/…/preview/inbound/PreviewCutter.java`, `orchestration/src/test/java/…/preview/inbound/GetAnalysisPreviewUseCaseTest.java`
- **Steps**:
  - [ ] Pass the `EnumMap` on unmodifiable instead of through `Map.copyOf`.
- **Tests**: `GetAnalysisPreviewUseCaseTest` checks that the sections come in `ReportSection` order.

### WP16: AnalysisPreviewToAnalysisPreviewDtoMapper as a MapStruct mapping

- **Status**: open
- **Depends on**: WP14, WP15
- **Files**: `bff/impl/src/main/java/…/mapper/AnalysisPreviewToAnalysisPreviewDtoMapper.java`, `bff/impl/src/test/java/…/mapper/AnalysisPreviewToAnalysisPreviewDtoMapperTest.java`
- **Steps**:
  - [ ] Rewrite the mapper as an abstract MapStruct mapping, with the key mapping chosen in WP14.
- **Tests**: `AnalysisPreviewToAnalysisPreviewDtoMapperTest`: the ID, the ticker, the date, the
  rating, lower-case section keys in source order, and the locked sections.

### WP17: ArchUnit rule for hand-written mappers

- **Status**: open
- **Depends on**: WP3, WP10, WP12, WP13, WP14, WP16
- **Files**: `src/test/java/…/ArchitectureTest.java`
- **Steps**:
  - [ ] Add the rule and its condition.
- **Tests**:
  - The rule passes on the code.
  - Before merging, show that it fails on a deliberately injected-only converter: put
    `StringToAnalysisIdMapper` back for one run.
  - Show that it selects the converters that are kept: `archRule.failOnEmptyShould=false` would hide
    an empty selection.

## Tests

Each work package's tests, plus `mise run e2e`: the report and preview pages read the same JSON.
`ProjectRulesTest` (WP8, WP9) and the ArchUnit rule (WP17) prove the issue's "forbidden by the
build", and so does `mise run build` passing with no `@Context` and no expression attribute left in
main code.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-checkstyle.md`, rules catalogue | Rows for `TAP-M2` and `TAP-M3`. The reasons: the data comes with the source object, and Java in a string is not compiled until MapStruct generates the code. Add both to the list of settings that limit where a check applies, and correct that list's count |
| Text | `docs/coding-convention/backend-java-package-structure.md`, "Where each class kind goes" (mapper rows) | A row "a mapper maps only", with rules `TAP-M2` and `TAP-M3`. No `@Context`: the delegate or a use case puts the data into the source object. No expression attributes: a computed value comes from the service or use case that builds the source, a nested value through `source = "a.b"` or a typed method, and an empty list from `nullValueIterableMappingStrategy`. A second row, with rule `hand_written_mappers_are_used_by_other_mappers`: a mapper with only `default` methods is a converter that another mapper uses (`uses`); the exception mappers are the exception |
| Text | `docs/coding-convention/backend-java-package-structure.md`, end of "Accepted duplicates" | Remove the sentence that names `StringToAnalysisIdMapper` as the last mapper sharing a name; say no mapper shares a name |
| Text | `artifact/backend/library/mapper/README.md`, "What it holds" | A row for `OptionalInstantToInstantMapper`, used by the identity adapter and the BFF |
| Text | `build-logic/checkstyle-rules/docs/ForbiddenAnnotationCheck.md` (new, from the template) | What it matches, the parameters, examples of both forms (the annotation, the attributes), the message, the project rules that use it |
| Text | `build-logic/checkstyle-rules/README.md`, "The checks" | A row for `ForbiddenAnnotationCheck` (`TAP-M2`, `TAP-M3`) |
| Screenshot | none | No page changes |

## Out of scope

- `openapi.json` and the frontend API types: the REST API does not change.

## Open questions

These are answered with the plan's choices below; the reviewer can overturn any of them.

- Where `AnalysisView` lives. **Choice:** in a new orchestration feature, `analysisview`, because its
  data spans two domains and `bff.delegate.impl` has no model package. The alternative is a model
  package in the BFF (`bff.delegate.impl.model`), which would be a new kind of package with an
  ArchUnit rule of its own.
- Whether `TAP-M3` also forbids `conditionExpression`, which the issue does not name and nothing
  uses. **Choice:** yes, since it is Java in a string as well.
- Removing injected-only `default` mappers. The issue says such a mapper is removed and its caller
  builds the value. **Choice:**
  - that applies only to the scalar converter `StringToAnalysisIdMapper`;
  - the four hand-written DTO mappers are rewritten as MapStruct mappings instead, since removing
    them would move their code into the delegates;
  - the exception mappers, which the REST convention requires, are exempt.
- `EnumToLowerCaseNameMapper` injected into `JsonlEventStoreAdapter` directly, besides the BFF mapper
  that uses it. **Choice:** it stays; the rule allows that.
