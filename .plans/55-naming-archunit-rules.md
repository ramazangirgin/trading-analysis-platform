# Plan: Project rules, part 7: naming by package and no beans in the model (G, D3, ArchUnit)

- **Issue**: #55 (Java: project-specific Checkstyle rules (custom checks))
- **Plan**: 7 of 7 for this issue; depends on: `55-checkstyle-rules-module` (only for the "Project
  rules" doc section it points from; the ArchUnit work itself is independent)
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The class names that follow from a class's package are checked, not only the package that follows
from a name: interfaces the services implement end in `UseCase`, outbound port interfaces end in
`Port`, port implementations end in `Adapter`, exceptions end in `Exception`, and model classes are
never Spring beans. These rules depend on packages and class hierarchies, so they go into
`ArchitectureTest` (tier 3 of #55), not Checkstyle. #51 is closed, so they are added here.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend tests (`ArchitectureTest`) | `backend-java-package-structure.md`: rule names are the `snake_case` field names, listed in "Where each class kind goes"; top-level classes only; a rule that selects nothing passes silently, so each is shown failing once |

## Design

What exists already (no change): `mappers_are_named_after_their_mapping` (`*To*Mapper`),
`mapper_packages_hold_only_mappers`, `row_packages_hold_only_row_records` (`*Row`),
`delegate_root_holds_only_delegate_impls` (`*ApiDelegateImpl`),
`controller_root_holds_only_controllers_and_delegates`, `exceptions_live_in_exception_packages`.
The issue's "exceptions live in `core/model`" is out of date: they live in `core.exception` (or
`bff.controller.api.error`), which is already enforced.

New rules:

| Rule (field name) | Selects | Should |
|---|---|---|
| `use_cases_are_named_use_case` | interfaces in `..inbound..` implemented by a class in `..service..` | have a simple name ending in `UseCase`. The other inbound types (`EventSubscription`, `RunEventListener`, handed out by use cases) are not implemented by services and are not selected |
| `ports_are_named_port` | interfaces in `domain.*.core.outbound..` implemented by a class in `domain.*.adapter..` | end in `Port`. Callback types like `RunEventSink` (implemented in core) are not selected |
| `port_adapters_are_named_adapter` | top-level classes in `domain.*.adapter..` implementing an outbound port | end in `Adapter` |
| `exceptions_are_named_exception` | top-level classes assignable to `Throwable` | end in `Exception` |
| `model_classes_are_not_beans` (D3) | classes in `..core.model..` and `orchestration.*.model..` | not be meta-annotated with `@Component` (covers `@Service`, `@Repository`, `@Configuration`) |

Each rule gets a `because(...)` naming the convention. The developer first runs each against
today's code; a class that fails is renamed if the name is simply wrong, or the rule's selection is
narrowed if the class is a legitimate exception, and the plan's PR says which.

## Work packages

### WP1: ArchUnit rules

- **Depends on**: none
- **Files**: `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/ArchitectureTest.java`,
  any class renamed because of a finding
- **Steps**:
  - [ ] Add the five rules in the matching sections (placement / naming).
  - [ ] Run `./gradlew :backend:test --tests '*ArchitectureTest'`; fix or narrow findings.
  - [ ] Show each rule failing on a deliberate violation (an inbound interface `Foo` implemented by
        a service, a `@Component` record in `core.model`, ...) and record it in the PR description.
- **Tests**: `ArchitectureTest`.

### WP2: Documentation

- **Depends on**: WP1
- **Files**: `docs/coding-convention/backend-java-package-structure.md`,
  `docs/coding-convention/backend-java-checkstyle.md`
- **Steps**:
  - [ ] Package structure doc, "Where each class kind goes": the five rules in the rows of their
        class kinds (use case, port, adapter, exception, model).
  - [ ] Checkstyle doc, "Project rules": one line that groups G and D3 are ArchUnit rules, linking
        the package structure doc.
- **Tests**: none.

## Tests

`mise run build` runs `ArchitectureTest` with the new rules; the PR description shows each failing
on a deliberate violation.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-package-structure.md` | New rule names in "Where each class kind goes" |
| Text | `docs/coding-convention/backend-java-checkstyle.md` | Pointer from "Project rules" to the ArchUnit rules |
| Screenshot | none | No UI change |

## Out of scope

- The `properties` package kind and its rules: plan 3 (`55-configuration-properties`).
