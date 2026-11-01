# Plan: Hand-written mappers only behind other mappers

- **Issue**: #277 (Mappers hold mapping only: forbid @Context and expression in mappers)
- **Plan**: 2 of 2 for this issue; depends on: 277-mappers-mapping-only-rules
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

A mapper that holds only `default` methods (a hand-written converter) exists only because another
mapper uses it, and an ArchUnit rule fails the build otherwise. `StringToAnalysisIdMapper` is gone,
and the four hand-written DTO mappers a delegate injects become MapStruct mappings. The REST API and
its JSON do not change (field order, key spelling and list order included).

## Affected parts and their conventions

- Backend: [package structure](../docs/coding-convention/backend-java-package-structure.md),
  [shared mapper library](../artifact/backend/library/mapper/README.md),
  [Changing a convention](../docs/coding-convention/README.md#changing-a-convention) (prove the new
  ArchUnit rule fails).

Paths below are under `artifact/backend/`; `…` is the module's package path.

## Design

Sorting of today's `default`-only mappers (`@Mapper` with no abstract method):

| Mapper | Users | Result |
|---|---|---|
| `AnalysisIdToStringMapper`, `AssetTypeToStringMapper`, `AnalystToStringMapper`, `StringToInstantMapper`, `StringToRunOutcomeMapper`, `StringToRatingMapper`, `StringToRunEventTypeMapper`, `RunnerOutputLineToRunStatsMapper`, `InstantToOptionalInstantMapper`, `OptionalInstantToInstantMapper`, `DurationToMillisMapper`, `EnumToLowerCaseNameMapper` | another mapper's `uses` | kept |
| `StringToAnalysisIdMapper` | injected into `AnalysesApiDelegateImpl`, `PublicAnalysesApiDelegateImpl` only | removed; the delegates write `new AnalysisId(id)` |
| `*ExceptionToApiExceptionMapper` (`mapper.error`) | `DomainExceptionHandler` only, by the [REST convention](../docs/coding-convention/backend-java-rest-api.md#errors) | kept, exempt from the rule |
| `UserAccountToUserDtoMapper`, `PriceHistoryToPriceHistoryDtoMapper`, `ReportToAnalysisReportDtoMapper`, `AnalysisPreviewToAnalysisPreviewDtoMapper` | injected into a delegate only | rewritten as abstract MapStruct mappings |

- **The four DTO mappers.** Moving their bodies into the delegates would put mapping into the
  delegates, so they become declarative mappings: nested sources (`key.ticker`, `id.value`,
  `username.value`), generated list and map methods, converters in `uses`:
  - `UserAccountToUserDtoMapper`: an abstract `UserRoleDto map(Role)` (`id` from `id.value`, the
    other role properties in `@BeanMapping(ignoreUnmappedSourceProperties = ...)`), and
    `OptionalInstantToInstantMapper` for `lockedUntil` / `lastLoginAt`. That converter gets its second
    module, so it moves from the identity adapter to `library.mapper` (`mappers_are_not_duplicated_across_modules`).
  - `ReportToAnalysisReportDtoMapper`: map keys lower-cased through `EnumToLowerCaseNameMapper`
    (`@MapMapping(keyQualifiedByName = "lowerCaseName")`; if the `sections` and `debates` map methods
    clash by erasure, typed key converters `ReportSectionToStringMapper` and
    `DebateSpeakerToStringMapper` in `uses` instead), `sources` sorted by name in a typed
    `List<String> map(Set<ReportSource>)`, `rating` by MapStruct's enum mapping.
  - `AnalysisPreviewToAnalysisPreviewDtoMapper`: the same key mapping as the report; the section
    order comes from the source: `PreviewCutter` hands its `EnumMap` on unmodifiable
    (`Collections.unmodifiableMap`) instead of `Map.copyOf`, which loses the order, so the mapper's
    `TreeMap` goes.
  - `PriceHistoryToPriceHistoryDtoMapper`: `ticker` and `tradeDate` from `key`, `points` generated.
- **The rule** `hand_written_mappers_are_used_by_other_mappers` in `ArchitectureTest` ("Mapping"
  section): a class annotated `@Mapper` whose methods are all non-abstract, outside
  `bff.delegate.impl.mapper.error`, has a dependent class in a `mapper` package that is neither
  itself nor its generated `*Impl`. The generated `*Impl` of the using mapper holds the converter as a
  constructor-injected field, so the dependency is visible to ArchUnit; a custom `ArchCondition` like
  `haveASimpleNameNoOtherMapperHas` states it. A mapper used through `uses` may still be injected
  elsewhere as well (`JsonlEventStoreAdapter` injects `EnumToLowerCaseNameMapper`).

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: Remove StringToAnalysisIdMapper

- **Status**: open
- **Depends on**: none
- **Files**: `bff/impl/src/main/java/…/mapper/StringToAnalysisIdMapper.java` (deleted), `bff/impl/src/main/java/…/AnalysesApiDelegateImpl.java`, `bff/impl/src/main/java/…/PublicAnalysesApiDelegateImpl.java`
- **Steps**:
  - [ ] Replace each `idMapper.map(id)` with `new AnalysisId(id)`.
- **Tests**: `AnalysesApiIntegrationTest`, `AnalysisSharingIntegrationTest` and the public analyses
  tests pass unchanged (an invalid ID still answers 404).

### WP2: OptionalInstantToInstantMapper into the mapper library

- **Status**: open
- **Depends on**: none
- **Files**: `domain/identity/adapter/src/main/java/…/persistence/mapper/OptionalInstantToInstantMapper.java` (deleted), `library/mapper/src/main/java/…/library/mapper/OptionalInstantToInstantMapper.java`, `domain/identity/adapter/src/main/java/…/persistence/mapper/UserToUserEntityMapper.java`, `library/mapper/src/test/java/…/library/mapper/OptionalInstantToInstantMapperTest.java`
- **Steps**:
  - [ ] Move the class; `UserToUserEntityMapper` imports it from the library.
- **Tests**: `OptionalInstantToInstantMapperTest` (empty to null, present to its value, no domain
  names); the identity adapter's repository tests pass unchanged.

### WP3: UserAccountToUserDtoMapper as a MapStruct mapping

- **Status**: open
- **Depends on**: WP2
- **Files**: `bff/impl/src/main/java/…/mapper/UserAccountToUserDtoMapper.java`, `bff/impl/src/test/java/…/mapper/UserAccountToUserDtoMapperTest.java`
- **Steps**:
  - [ ] Rewrite as in "Design".
- **Tests**: `UserAccountToUserDtoMapperTest`: every field, roles in the given order, empty and present
  `lockedUntil` / `lastLoginAt`.

### WP4: PriceHistoryToPriceHistoryDtoMapper as a MapStruct mapping

- **Status**: open
- **Depends on**: none
- **Files**: `bff/impl/src/main/java/…/mapper/PriceHistoryToPriceHistoryDtoMapper.java`, `bff/impl/src/test/java/…/mapper/PriceHistoryToPriceHistoryDtoMapperTest.java`
- **Steps**:
  - [ ] Rewrite as in "Design".
- **Tests**: `PriceHistoryToPriceHistoryDtoMapperTest`: ticker, trade date, points in order with all
  values.

### WP5: ReportToAnalysisReportDtoMapper as a MapStruct mapping

- **Status**: open
- **Depends on**: none
- **Files**: `bff/impl/src/main/java/…/mapper/ReportToAnalysisReportDtoMapper.java`, `bff/impl/src/test/java/…/mapper/ReportToAnalysisReportDtoMapperTest.java`
- **Steps**:
  - [ ] Rewrite as in "Design" (plus the two key converters only if the erasure clash needs them).
- **Tests**: `ReportToAnalysisReportDtoMapperTest`: lower-case keys in enum order for sections and
  debates, sources sorted by name, rating with and without value, `modifiedAt`.

### WP6: Preview sections keep their order

- **Status**: open
- **Depends on**: none
- **Files**: `orchestration/src/main/java/…/preview/inbound/PreviewCutter.java`, `orchestration/src/test/java/…/preview/inbound/GetAnalysisPreviewUseCaseTest.java`
- **Steps**:
  - [ ] Hand the `EnumMap` on unmodifiable instead of `Map.copyOf`.
- **Tests**: `GetAnalysisPreviewUseCaseTest` asserts the sections in `ReportSection` order.

### WP7: AnalysisPreviewToAnalysisPreviewDtoMapper as a MapStruct mapping

- **Status**: open
- **Depends on**: WP5, WP6
- **Files**: `bff/impl/src/main/java/…/mapper/AnalysisPreviewToAnalysisPreviewDtoMapper.java`, `bff/impl/src/test/java/…/mapper/AnalysisPreviewToAnalysisPreviewDtoMapperTest.java`
- **Steps**:
  - [ ] Rewrite as in "Design", with the key mapping WP5 chose.
- **Tests**: `AnalysisPreviewToAnalysisPreviewDtoMapperTest`: id, ticker, date, rating, lower-case
  section keys in source order, locked sections.

### WP8: ArchUnit rule for hand-written mappers

- **Status**: open
- **Depends on**: WP1, WP3, WP4, WP5, WP7
- **Files**: `src/test/java/…/ArchitectureTest.java`
- **Steps**:
  - [ ] Add the rule and its condition as in "Design".
- **Tests**: the rule passes on the code; before merging, show it fails on a deliberately injected-only
  converter (`StringToAnalysisIdMapper` put back for one run) and that it selects the kept converters
  (`archRule.failOnEmptyShould=false` would hide an empty selection).

## Tests

Each work package's tests; `mise run e2e` passes (the report and preview pages read the same JSON).

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-package-structure.md`, "Where each class kind goes" | Row: a mapper with only `default` methods is a converter used by another mapper (`uses`); the exception mappers excepted; rule `hand_written_mappers_are_used_by_other_mappers` |
| Text | `docs/coding-convention/backend-java-package-structure.md`, end of "Accepted duplicates" | The sentence on `StringToAnalysisIdMapper` as the last same-named mapper goes; say no mapper name is shared |
| Text | `artifact/backend/library/mapper/README.md`, "What it holds" | Row for `OptionalInstantToInstantMapper` (identity adapter and BFF) |
| Screenshot | none | No page changes |

## Out of scope

- `@Context` and expression attributes, `TAP-M2`, `TAP-M3`: plan 1, `277-mappers-mapping-only-rules`.

## Open questions

- The issue says an injected-only `default` mapper is removed and its caller builds the value. The
  plan applies that to the scalar converter `StringToAnalysisIdMapper` only, rewrites the four
  hand-written DTO mappers as MapStruct mappings instead (their bodies would otherwise move into the
  delegates), and exempts the exception mappers, which the REST convention requires. Agreed?
- `EnumToLowerCaseNameMapper` stays injected into `JsonlEventStoreAdapter` besides its `uses` in the
  BFF; the rule allows that. Should the adapter's direct use go too?
