# Plan: Collaborators named after their type: the check and rule TAP-D9

- **Issue**: #275 (Name injected collaborators after their type: enforce, document, refactor)
- **Plan**: 1 of 3 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

A variable whose type is a collaborator (`*Service`, `*UseCase`, `*Port`, `*Mapper`, `*Repository`,
`*Adapter`) must be named after that type in lower camel case: `UserRepositoryPort
userRepositoryPort`, not `users`. Checkstyle reports a wrong name as `TAP-D9`, in main and test code.
The rule is documented for developers and for the agents, so new code uses these names from now on.
The existing code is renamed by plans 2 and 3 of this issue; until then a temporary filter keeps
`TAP-D9` off the modules that still have findings.

## Affected parts and their conventions

- Custom Checkstyle checks: [build-logic/checkstyle-rules/README.md](../build-logic/checkstyle-rules/README.md)
  ("The contract of a check", "Tests", "Adding a check", "Adding a project rule").
- Backend Checkstyle: [backend-java-checkstyle.md](../docs/coding-convention/backend-java-checkstyle.md)
  ("Project rules").
- Agent prompts: [agentic-development.md](../docs/agentic-development.md).

## Design

**Checkstyle, not ArchUnit.** ArchUnit sees fields and constructor parameters (with `-parameters`)
but no local variables, and it does not import test code here. A Checkstyle check reads every
declaration with its declared type in main and test sources, which is what the rule needs.

**A new generic check, `VariableNamedAfterTypeCheck`** in `tr.girgin.checkstyle`. It knows nothing
about the project; the rule configures it.

- It visits `VARIABLE_DEF` (fields, locals, for-each and resource variables), `PARAMETER_DEF`
  (constructor, method and typed lambda parameters) and `RECORD_COMPONENT_DEF`.
- The declared type is the last segment of the type's name, without type arguments
  (`Fakes.FakeUserRepository` → `FakeUserRepository`, `FooRepository<X>` → `FooRepository`). A
  declaration is selected when that name matches one of `types` (`SimpleNamePattern`, `*Service`).
- Not checked: `var`, array types, `static final` fields (constants have their own naming rule) and
  unnamed variables (`_`).
- The expected name is the type's simple name with its leading capitals lowered, the last one kept
  when a lower-case letter follows (`AnalysisService` → `analysisService`, `AIService` →
  `aiService`).
- Parameters: `types` (required, a list of `SimpleNamePattern`s), `allowQualifier` (default
  `false`): also accept a name that ends with the type's simple name after a lower-case qualifier
  (`strictUserManagementService`, `specJsonMapper`), and `suggestion`.
- One message key, `variable.named.after.type`: `TAP-D9: 'users' of type 'UserRepositoryPort' is
  not named 'userRepositoryPort' (or '<qualifier>UserRepositoryPort'). <suggestion>`; the part in
  parentheses only with `allowQualifier`.

**Rule `TAP-D9`** (group `D`, Spring and dependency injection; `D8` is the last id used): the check
with `types = *Service, *UseCase, *Port, *Mapper, *Repository, *Adapter`, `allowQualifier = true`,
main and test sources.

- The suffixes are the issue's six. Types from libraries with these suffixes count too
  (`JsonMapper`, `ExecutorService`, `SecurityContextRepository`, `WatchService`): the check has no
  type resolution, and one name per type is the point of the rule either way.
- The qualifier is allowed because a scope can hold two collaborators of the same type (the tests
  build differently configured services side by side: `strict`, `untouched`, `bundled`), and a
  local named exactly like a field of the class would break `HiddenField`.

**Temporary scope.** A rule goes in at `error` with its findings fixed in the same pull request,
but the existing code has about 500 findings in 150 files (a regex count on `main` at f3def285;
the check is the authority), too many for one reviewable pull request. So this plan adds, next to
the other `SuppressionSingleFilter`s at the top of `checkstyle.xml`, a filter for `TAP-D9` with
`files` matching
`[\\/]artifact[\\/]backend[\\/](domain|library[\\/]mapper|bff[\\/]impl|orchestration|src[\\/]test)[\\/]`.
The rule then applies to `bff/api`, `library/persistence` and the application's main sources, which
have no findings today. Plan 2 renames the domains and `library/mapper` and drops them from the
pattern; plan 3 renames the rest and deletes the filter. `ProjectRulesTest` copies the fixtures to a
temporary folder that the pattern does not match, so it proves the rule meanwhile.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: The check

- **Status**: done
- **Depends on**: none
- **Files**: `build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/VariableNamedAfterTypeCheck.java`,
  `build-logic/checkstyle-rules/src/main/resources/tr/girgin/checkstyle/messages.properties`,
  `build-logic/checkstyle-rules/src/test/java/tr/girgin/checkstyle/VariableNamedAfterTypeCheckTest.java`,
  `build-logic/checkstyle-rules/src/test/resources/checks/VariableNamedAfterTypeCheck/Declarations.java`,
  `build-logic/checkstyle-rules/src/test/resources/checks/VariableNamedAfterTypeCheck/Qualifier.java`,
  `build-logic/checkstyle-rules/src/test/resources/checks/VariableNamedAfterTypeCheck/NotChecked.java`,
  `build-logic/checkstyle-rules/docs/VariableNamedAfterTypeCheck.md`,
  `build-logic/checkstyle-rules/README.md`
- **Steps**:
  - [x] Reuse `SimpleNamePattern` and `SourceNames` from `support/` for the type patterns and the
    type's last segment.
- **Tests**: `VariableNamedAfterTypeCheckTest`: every declaration kind reported with a wrong name
  (field, constructor, method and lambda parameter, local, for-each, resource, record component),
  with a qualified and a generic type; not reported: the exact name, a qualifier with and without
  `allowQualifier`, `var`, an array, a `static final` field, `_`, a type no pattern matches, a
  name that only contains the type; the acronym case; a missing or invalid `types` fails the
  configuration; the message with and without `suggestion`. `DocumentationTest` passes for the new
  document and index row; the module's coverage gate stays at 100%.

### WP2: Rule TAP-D9 and the agents' guidance

- **Status**: done
- **Depends on**: WP1
- **Files**: `config/checkstyle/checkstyle.xml`,
  `build-logic/checkstyle-rules/src/test/resources/rules/TAP-D9/Violation.java`,
  `build-logic/checkstyle-rules/src/test/resources/rules/TAP-D9/Compliant.java`,
  `docs/coding-convention/backend-java-checkstyle.md`, `docs/coding-convention/README.md`,
  `CLAUDE.md`, `scripts/agent/prompts/implement.md`
- **Steps**:
  - [x] The `TAP-D9` module at the end of `TreeWalker`, with a `suggestion` that gives the rule and
    an example (`userRepositoryPort`, `strictUserManagementService`).
  - [x] The temporary filter from "Design".
- **Tests**: `ProjectRulesTest` proves `TAP-D9` in main and test sources (`Violation.java` covers a
  field, a constructor parameter and a local; `Compliant.java` the exact names and a qualified one).
  `mise run check` passes, so nothing outside the filter is reported.

## Tests

Each work package's tests.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `build-logic/checkstyle-rules/docs/VariableNamedAfterTypeCheck.md` (new, WP1) | reference document from `docs/_template.md`: what it matches and skips, the parameters, two examples (with and without `allowQualifier`), the message, `TAP-D9` |
| Text | `build-logic/checkstyle-rules/README.md` "The checks" (WP1) | row for `VariableNamedAfterTypeCheck`, project rule `TAP-D9` |
| Text | `docs/coding-convention/backend-java-checkstyle.md` | "Naming": a bullet for collaborator names linking to `TAP-D9`; rules catalogue: the `TAP-D9` row (rule, main and test, check, parameters, reason from "Design", bad / good `UserRepositoryPort users` / `UserRepositoryPort userRepositoryPort`); "Suppressing a finding": the temporary `TAP-D9` filter in the list of settings that limit where a check applies (six becomes seven), saying it goes away with #275 |
| Text | `docs/coding-convention/README.md` | the Checkstyle row's "Covers" names collaborator names |
| Text | `CLAUDE.md` "Rules that the build checks" | the Checkstyle bullet gets "an injected collaborator is named after its type (`userRepositoryPort`, `TAP-D9`)" |
| Text | `scripts/agent/prompts/implement.md` "Rules" | a line like the `TAP-D6` one: name a collaborator after its type, with an optional qualifier |
| Text | `.claude/skills/plan-from-issue/SKILL.md` section 3, "Conventions" (by hand: the agents cannot edit `.claude/`, the package marks it "not done") | a plan that names new fields, parameters or locals of a collaborator type uses the `TAP-D9` names |
| Screenshot | none | no page changes |

## Out of scope

- Renaming the existing code: plans 2 (domains, `library/mapper`) and 3 (`bff/impl`,
  `orchestration`, application tests), which also remove the temporary filter.
- Other suffixes (`*Client`, `*Factory`, `*Registry`, `*Properties`) and pattern variables
  (`instanceof Foo foo`): not asked for by the issue; a later rule can widen `types`.

## Open questions

None. The developer approved the three-plan split with the temporary `TAP-D9` filter.
