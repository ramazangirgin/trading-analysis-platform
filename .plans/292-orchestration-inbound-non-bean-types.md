# Plan: Orchestration inbound packages hold only beans

- **Issue**: #292 (Orchestration inbound packages: move non-bean types (e.g. PreviewSettings) to model)
- **Plan**: 1 of 1 for this issue; depends on: `.plans/273-inbound-holds-only-interfaces.md` (PR #290), which changes the same lines of `ArchitectureTest` and of the package-structure document
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

An orchestration feature's `inbound` package holds only Spring beans (the `*UseCase` beans and
their package-private helper beans such as `PreviewCutter`), its `model` package only the
composite models, and configuration properties a new `config` package. ArchUnit fails the build
when a type lands in the wrong one, and the package-structure document says where each goes.

## Affected parts and their conventions

- Backend, `:backend:orchestration` and `:backend` (`ArchitectureTest`):
  [backend-java-package-structure.md](../docs/coding-convention/backend-java-package-structure.md),
  [backend-java-checkstyle.md](../docs/coding-convention/backend-java-checkstyle.md).

## Design

- **What is in orchestration `inbound` today**: every top-level type is a `@Service` `*UseCase`,
  except `preview.inbound.PreviewCutter` (a `@Component` helper bean, it stays) and
  `preview.inbound.PreviewSettings` (a `@ConfigurationProperties` record). So `PreviewSettings` is
  the only non-bean type to move; the issue's second question needs no other move.
- **`PreviewSettings` does not go to `model`**: following the decision on the issue, `model` holds
  composites of several domains' data, and `PreviewSettings` is not one. It also validates
  `excerptLength` and copies `sections` in its constructor, which `models_are_data` forbids, and that
  validation is what makes a bad `platform.analysis.preview.excerpt-length` fail at startup.
- **New feature sub-package `orchestration.<f>.config`** for `@ConfigurationProperties` records,
  named like `bff.controller.api.config` and `bff.delegate.impl.config`. `PreviewSettings` moves to
  `orchestration.preview.config`, unchanged (it is already public; its two constants stay
  package-private, used only by its own test). `@EnableConfigurationProperties(PreviewSettings.class)`
  stays on `GetAnalysisPreviewUseCase`; the property prefix does not change.
- **ArchUnit rules** in `ArchitectureTest`, next to `orchestration_use_cases_are_concrete_beans`:
  - `orchestration_inbound_packages_hold_only_beans`: every top-level class in
    `ORCHESTRATION_INBOUND` is meta-annotated with `@Component` (covers `@Service`).
  - `orchestration_config_packages_hold_only_configuration_properties`: every top-level class in
    `orchestration.*.config..` is annotated with `@ConfigurationProperties`.
  - `configuration_properties_live_in_config_packages`: every class annotated with
    `@ConfigurationProperties` resides in a `..config..` package (today only `PreviewSettings`;
    elsewhere settings are read with `@Value`).
  - `orchestration_features_follow_the_domain_core_shape` allows `config` next to `inbound` and
    `model`, and its `because` names it.
- **Decided**: the new sub-package is named `config`.
- **Decided**: `configuration_properties_live_in_config_packages` holds for the whole backend.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: Move PreviewSettings to the config package

- **Status**: done
- **Depends on**: none
- **Files**:
  - `artifact/backend/orchestration/src/main/java/tr/girgin/backend/trading/analysis/platform/orchestration/preview/config/PreviewSettings.java` (moved from `preview/inbound/`)
  - `artifact/backend/orchestration/src/test/java/tr/girgin/backend/trading/analysis/platform/orchestration/preview/config/PreviewSettingsTest.java` (moved from `preview/inbound/`)
  - `artifact/backend/orchestration/src/main/java/tr/girgin/backend/trading/analysis/platform/orchestration/preview/inbound/PreviewCutter.java`
  - `artifact/backend/orchestration/src/main/java/tr/girgin/backend/trading/analysis/platform/orchestration/preview/inbound/GetAnalysisPreviewUseCase.java`
  - `artifact/backend/orchestration/src/test/java/tr/girgin/backend/trading/analysis/platform/orchestration/preview/inbound/GetAnalysisPreviewUseCaseTest.java`
  - `artifact/backend/orchestration/src/test/java/tr/girgin/backend/trading/analysis/platform/orchestration/preview/inbound/ListAnalysisPreviewsUseCaseTest.java`
- **Steps**:
  - [x] `git mv` both files so the history follows them, change their package and import the record where it is used.
- **Tests**: the existing `PreviewSettingsTest` (binding, defaults, bounds) and the two use case
  tests pass unchanged apart from the import; `mise run gradle :backend:orchestration:test`.

### WP2: ArchUnit rules for the orchestration packages

- **Status**: done
- **Depends on**: WP1
- **Files**:
  - `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/ArchitectureTest.java`
- **Steps**:
  - [x] Add the three rules and widen `orchestration_features_follow_the_domain_core_shape` as in "Design"; reuse `ORCHESTRATION_INBOUND` and add a constant for the config packages.
  - [x] If dependency analysis flags the `ConfigurationProperties` import in `:backend`'s tests, declare the artifact it comes from.
- **Tests**: `ArchitectureTest` passes on the moved code. Prove each new rule fails once by hand
  (a plain record in `preview.inbound`, a plain class in `preview.config`, `PreviewSettings` back in
  `inbound`), then revert; no permanent negative test, like the existing rules.

## Tests

Each work package's tests; `mise run check` and `mise run build` green.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-package-structure.md`, "Package layout" | under `orchestration/<feature>/`: `inbound/` holds `*UseCase` beans and their helper beans only; `model/` holds composite models (types combining data of several domains, e.g. `AnalysisView`); new `config/` line for `@ConfigurationProperties` records |
| Text | same document, "Where each class kind goes" | rows for the three new rules; the "Orchestration feature sub-packages" row lists `inbound`, `model`, `config` |
| Screenshot | none | no page changes |

## Out of scope

- Domain core `inbound` / `outbound` and the `model` rules: #273 (PR #290).
- Moving `@EnableConfigurationProperties` into a `@Configuration` class of its own, and turning the
  `@Value` settings of adapters and the BFF into `@ConfigurationProperties` records.

## Open questions

None.
