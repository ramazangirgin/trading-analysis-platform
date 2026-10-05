# Plan: Project Checkstyle rules, part 2: logging (L)

- **Issue**: #55 (Java: project-specific Checkstyle rules (custom checks))
- **Plan**: 2 of 7 for this issue; depends on: `55-checkstyle-rules-module`
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The build fails when a logger is not declared as
`private static final Logger log = LoggerFactory.getLogger(<OwnClass>.class)` (a logger copied from
another class logs under the wrong name), and when a log call builds its message by concatenation,
`String.format` or `.formatted(...)` instead of `{}` placeholders. Both rules are instances of two new
generic checks, documented and fully tested like the first one.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Custom checks module (`build-logic/checkstyle-rules`) | The contract from plan 1: generic parameters, no project knowledge in code, `support/` helpers reused (member patterns, import resolution), one test class and fixture folder per check, 100% coverage, a reference document per check, a row in the index |
| Checkstyle config | `TAP-*` instances in the `Project rules (#55)` section; fixtures under `rules/<id>/` for `ProjectRulesTest` |
| Docs | `backend-java-checkstyle.md` rules catalogue; `backend-java-checkstyle-custom-checks.md` index; `backend-java-checkstyle-checks/<CheckName>.md` |

## Design

### `FieldDeclarationShapeCheck` (new)

For every field of a given type, requires modifiers, a name and an initializer.

| Parameter | Type | Default | Meaning |
|---|---|---|---|
| `type` | type name | (required) | The field type the check applies to, simple or qualified (`org.slf4j.Logger`) |
| `requiredModifiers` | list | empty | Modifiers the field must have (`private, static, final`) |
| `nameFormat` | regex | `.*` | The field name must match (`^log$`) |
| `initializer` | member pattern (plan 1 syntax) | empty (not checked) | The field must be initialised by a call matching it (`LoggerFactory#getLogger(1)`) |
| `initializerArgument` | `ANY` / `ENCLOSING_CLASS_LITERAL` | `ANY` | With `ENCLOSING_CLASS_LITERAL`, the call's single argument must be `<Name>.class` of the class (or record, enum) that declares the field, nested classes included |
| `suggestion` | string | empty | Appended to the message |

One finding per violated aspect (modifiers, name, initializer, argument), each with its own message
key, so the message says which part is wrong.

### `CallArgumentExpressionCheck` (new)

Forbids kinds of expressions in the arguments of given calls.

| Parameter | Type | Default | Meaning |
|---|---|---|---|
| `calls` | list of member patterns | (required) | The calls whose arguments are checked. The receiver part matches the receiver expression as written, so `log#info()` matches `log.info(...)`; `*` for any receiver |
| `forbidConcatenation` | boolean | `true` | A `+` with a string literal operand anywhere in an argument, including over wrapped lines |
| `forbiddenCalls` | list of member patterns | empty | Calls not allowed inside an argument (`String#format(), *#formatted()`) |
| `arguments` | `ALL` / `FIRST` | `ALL` | Which arguments are checked |
| `suggestion` | string | empty | Appended to the message |

It works on the syntax tree, so a log call the formatter wraps over several lines is checked like a
one-line call.

### Rules

| Id | Rule | Check and parameters | Sources | Hits today |
|---|---|---|---|---|
| TAP-L2 | Logger declaration | `FieldDeclarationShapeCheck`: `type = org.slf4j.Logger`, `requiredModifiers = private, static, final`, `nameFormat = ^log$`, `initializer = LoggerFactory#getLogger(1)`, `initializerArgument = ENCLOSING_CLASS_LITERAL` | main and test | 11 loggers, all correct |
| TAP-L3 | Placeholders, not string building, in log calls | `CallArgumentExpressionCheck`: `calls = log#trace(), log#debug(), log#info(), log#warn(), log#error()`, `forbidConcatenation = true`, `forbiddenCalls = String#format(), *#formatted()` | main and test | 0 |

`ConstantName` today accepts `log` or `LOG`; with TAP-L2 it is `log` only, so its format becomes
`^(log|[A-Z][A-Z0-9]*(_[A-Z0-9]+)*)$`.

**L4** (no API keys, tokens, secrets or passwords in log calls without the masking helper) needs
the helper from #46, still open. It moves there: `CallArgumentExpressionCheck` can carry it later
(a parameter for forbidden identifiers would be added then, with its tests and docs).

## Work packages

### WP1: `FieldDeclarationShapeCheck`

- **Depends on**: none (plan 1 merged)
- **Files**: `build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/FieldDeclarationShapeCheck.java`,
  `messages.properties`, `FieldDeclarationShapeCheckTest.java`, `src/test/resources/checks/FieldDeclarationShapeCheck/*`
- **Steps**:
  - [ ] Implement with the `support/` helpers; validate parameters.
  - [ ] Tests: each parameter alone and together; nested class, record and enum loggers; a logger
        copied from another class; a local variable of the type (not a field, not checked); a field
        without initializer; qualified and simple `type`; invalid `initializer` pattern.
- **Tests**: as listed; 100% coverage.

### WP2: `CallArgumentExpressionCheck`

- **Depends on**: none
- **Files**: `CallArgumentExpressionCheck.java`, `messages.properties`, `CallArgumentExpressionCheckTest.java`,
  `src/test/resources/checks/CallArgumentExpressionCheck/*`
- **Steps**:
  - [ ] Implement; concatenation detection through parentheses and wrapped lines; `arguments` modes.
  - [ ] Tests: concatenation in the first and a later argument; numeric `+` (not reported);
        `String.format` and `.formatted`; `{}` placeholders with arguments (compliant); a call on a
        different receiver (`other.info("a" + b)`, not reported); wildcard receiver.
- **Tests**: as listed; 100% coverage.

### WP3: Rules, fixtures and docs

- **Depends on**: WP1, WP2
- **Files**: `config/checkstyle/checkstyle.xml`, `src/test/resources/rules/TAP-L2/*`, `rules/TAP-L3/*`,
  `docs/coding-convention/backend-java-checkstyle-checks/FieldDeclarationShapeCheck.md` (new),
  `docs/coding-convention/backend-java-checkstyle-checks/CallArgumentExpressionCheck.md` (new),
  `docs/coding-convention/backend-java-checkstyle-custom-checks.md`, `docs/coding-convention/backend-java-checkstyle.md`
- **Steps**:
  - [ ] Add the two instances and the `ConstantName` change.
  - [ ] Fixtures for `ProjectRulesTest`.
  - [ ] Reference documents from the template, each with configuration examples for at least two
        conditions (L2 and, for example, a `java.util.logging.Logger` or a `Clock` field;
        L3 and, for example, `Objects.requireNonNull` messages).
  - [ ] Index rows; catalogue rows L2, L3; note L4 → #46; logger naming in the Naming section.
  - [ ] Comment on #46 with how L4 is added once the helper exists.
  - [ ] Backend Checkstyle clean.
- **Tests**: `ProjectRulesTest`, `DocumentationTest`.

## Tests

`mise run build`: unit tests of both checks at 100% coverage, `ProjectRulesTest` with TAP-L2 and
TAP-L3 fixtures, `DocumentationTest`, backend Checkstyle clean.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-checkstyle-checks/` | Two new reference documents |
| Text | `docs/coding-convention/backend-java-checkstyle-custom-checks.md` | Two index rows; TAP-L1's check row lists nothing new |
| Text | `docs/coding-convention/backend-java-checkstyle.md` | Catalogue rows L2, L3; L4 → #46; logger name `log` only |
| Screenshot | none | No UI change |

## Out of scope

- L4: #46. Other groups: plans 3 to 7.
