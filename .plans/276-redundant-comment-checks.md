# Plan: Checkstyle and ruff rules for redundant comments

- **Issue**: #276 (Redundant comments: enforce, add LLM planning and review steps, document, clean up)
- **Plan**: 1 of 9 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The build fails on the comments a tool can judge without guessing: any Javadoc in backend code
(`package-info.java` aside), ports included, a comment whose words all appear in the statement below
it, a `TODO` without an issue number, and commented-out code in ta-runner. Checkstyle no longer
requires Javadoc on the ports. Every backend module whose existing comments plans 3 to 9 clean up is
exempt from the new rules through a temporary filter, one per cleanup plan, which that plan removes;
a new module, and every module once cleaned, is checked at once.

## Affected parts and their conventions

- Custom checks: [build-logic/checkstyle-rules/README.md](../build-logic/checkstyle-rules/README.md)
  (contract of a check, tests, adding a check, adding a project rule), [docs/_template.md](../build-logic/checkstyle-rules/docs/_template.md)
- Backend Checkstyle: [backend-java-checkstyle.md](../docs/coding-convention/backend-java-checkstyle.md),
  [Changing a convention](../docs/coding-convention/README.md#changing-a-convention)
- ta-runner: [ta-runner-python-package-structure.md](../docs/coding-convention/ta-runner-python-package-structure.md)
- All: [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md)

## Design

### Which rules are precise enough

| Issue item | Decision |
|---|---|
| Javadoc only where Checkstyle requires it | Decided (developer): no Javadoc is required any more, ports included. `MissingJavadocPortType` and its scope filter go; `TAP-J1` reports every Javadoc except the one of `package-info.java` (a module root's role, [package structure](../docs/coding-convention/backend-java-package-structure.md)). An error case a port's `@throws` named is visible through the exception type the method throws; a why that matters becomes a `//` comment |
| A required Javadoc is one sentence, no `@param` / `@return` | Moot: only `package-info.java` keeps Javadoc, and it stays outside the rules (developer). The built-in Javadoc form checks (`SummaryJavadoc`, `JavadocMethod`, …) stay for it |
| `//` only for an allow-list of markers | Not adopted: a `//` comment is where a why goes (also the why of a removed Javadoc), so a ban would remove the comments the rule wants. `TAP-C2` covers the `TODO` marker |
| `TODO` with an issue number | Precise: `TAP-C2`, built-in [`TodoComment`](https://checkstyle.org/checks/misc/todocomment.html), `format` `\b(TODO|FIXME)\b(?!:?\s*\(?#\d+)`, message starting with the id |
| A comment that repeats the next line | Precise enough as a heuristic that reports only when every word is in the code: `TAP-C1` |
| Python | ruff `ERA` (commented-out code) in ta-runner only (developer). No ruff rule forbids or limits a docstring (pydocstyle checks the form, and its `D1` rules require docstrings), so docstrings stay with the review |
| Shell | No tool of the repository checks shell comments, and none has such a rule: review only |

### New checks (`tr.girgin.checkstyle`, generic as the module's contract asks)

- `ForbiddenJavadocCheck`: reports a Javadoc on a declaration of the configured tokens (default:
  every declaration token). Finds the Javadoc of a declaration as `InvalidJavadocPosition` does
  (comment nodes, `JavadocUtil.isJavadocComment`).
- `CommentRepeatsCodeCheck`: takes a comment that stands on lines of its own (consecutive `//` lines,
  or a `/* */` that is not Javadoc) and the statement or declaration that starts on the next line of
  code. Words: letters and digits, lower case; identifiers and keywords of the code split at camel
  case, digits and underscores; a trailing `s` dropped on both sides; the words in `ignoredWords`
  (default: English function words such as `a`, `an`, `the`, `of`, `to`, `is`, `and`, `or`, `for`,
  `in`, `on`, `with`, `this`, `it`) are dropped from the comment. Reports, on the line of that
  statement, when a word is left and each one occurs in the code. A comment at the end of a code
  line (a suppression's reason) is not checked. Reporting on the statement's line lets a fixture
  mark it with `// violation`.

Both take `suggestion`, as the other checks do.

### Configuration (`config/checkstyle/checkstyle.xml`)

- `MissingJavadocType` (`MissingJavadocPortType`) and its `SuppressionSingleFilter` are removed.
- `TAP-J1`: `ForbiddenJavadocCheck`, every declaration token except `PACKAGE_DEF`, everywhere.
- `TAP-C1`: `CommentRepeatsCodeCheck`; `TAP-C2`: `TodoComment` as above.
- The pending cleanup: one `SuppressionSingleFilter` per cleanup plan, `message` `^TAP-[JC]\d`,
  `files` its modules, each under a one-line XML comment naming its plan file. The plan named
  removes its block:

| Modules (`artifact/backend/…`) | Removed by |
|---|---|
| `domain/analysis/core` | `.plans/276-comments-cleanup-analysis-core.md` |
| `domain/analysis/adapter`, `library/mapper`, `orchestration` | `.plans/276-comments-cleanup-analysis-adapter-orchestration.md` |
| `domain/identity` | `.plans/276-comments-cleanup-identity.md` |
| `domain/report`, `domain/catalog` | `.plans/276-comments-cleanup-report-catalog.md` |
| `domain/settings`, `library/persistence` | `.plans/276-comments-cleanup-settings-persistence.md` |
| `bff` | `.plans/276-comments-cleanup-bff.md` |
| the root module (`artifact/backend/src/`) | `.plans/276-comments-cleanup-backend-root.md` |

The fixture paths of `ProjectRulesTest` contain no `artifact/backend`, so the pending filters do not
hide them.

`artifact/ta-runner/pyproject.toml` adds `ERA` to ruff's `select`.

### What the rules cannot catch

- a comment that says what the code says in other words (`persist` for `save`), repeats more than the
  next statement, a type or a documented convention, or sits at the end of a line;
- a comment that is wrong or outdated (the issue's example: a strict format the constructor no
  longer checks), and a why nobody needs;
- `package-info.java` Javadoc; Python docstrings and comments other than commented-out code; shell,
  TypeScript, Vue, SQL and YAML comments; the custom checks module's own sources (Checkstyle does not
  check them).

The review step (plan 2) and the developer's review cover them.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: ForbiddenJavadocCheck

- **Status**: open
- **Depends on**: none
- **Files**: `build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/ForbiddenJavadocCheck.java`,
  `build-logic/checkstyle-rules/src/main/resources/tr/girgin/checkstyle/messages.properties`,
  `build-logic/checkstyle-rules/src/test/java/tr/girgin/checkstyle/ForbiddenJavadocCheckTest.java`,
  `build-logic/checkstyle-rules/src/test/resources/checks/ForbiddenJavadocCheck/`,
  `build-logic/checkstyle-rules/docs/ForbiddenJavadocCheck.md`, `build-logic/checkstyle-rules/README.md`
- **Steps**:
  - [ ] The check, its tokens and its message
- **Tests**: `ForbiddenJavadocCheckTest`: every token, a token left out, a block comment and a `//`
  comment that are not reported, the message with and without `suggestion`; 100% coverage

### WP2: CommentRepeatsCodeCheck

- **Status**: open
- **Depends on**: WP1
- **Files**: `build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/CommentRepeatsCodeCheck.java`,
  `build-logic/checkstyle-rules/src/main/resources/tr/girgin/checkstyle/messages.properties`,
  `build-logic/checkstyle-rules/src/test/java/tr/girgin/checkstyle/CommentRepeatsCodeCheckTest.java`,
  `build-logic/checkstyle-rules/src/test/resources/checks/CommentRepeatsCodeCheck/`,
  `build-logic/checkstyle-rules/docs/CommentRepeatsCodeCheck.md`, `build-logic/checkstyle-rules/README.md`
- **Steps**:
  - [ ] Word splitting and matching as in Design
- **Tests**: `CommentRepeatsCodeCheckTest`: `// save the user` above `userRepositoryPort.save(user);`
  reported, a why (`// the platform may have gone`) not, plurals, a comment of only ignored words, a
  multi-line `//` comment, a `/* */` comment, a trailing comment, a comment before a declaration and
  before a closing brace; 100% coverage

### WP3: Javadoc rule TAP-J1

- **Status**: open
- **Depends on**: WP1
- **Files**: `config/checkstyle/checkstyle.xml`,
  `build-logic/checkstyle-rules/src/test/resources/rules/TAP-J1/`,
  `docs/coding-convention/backend-java-checkstyle.md`
- **Steps**:
  - [ ] Remove `MissingJavadocPortType` and its filter; add `TAP-J1` and the seven pending blocks
- **Tests**: `ProjectRulesTest` on the fixtures (a Javadoc on a port interface, a service and a test
  method reported; a `package-info.java` Javadoc and a `/* */` comment not); `mise run check` passes
  on main's code

### WP4: Comment rules TAP-C1 and TAP-C2

- **Status**: open
- **Depends on**: WP2, WP3
- **Files**: `config/checkstyle/checkstyle.xml`,
  `build-logic/checkstyle-rules/src/test/resources/rules/TAP-C1/`,
  `build-logic/checkstyle-rules/src/test/resources/rules/TAP-C2/`,
  `docs/coding-convention/backend-java-checkstyle.md`
- **Steps**:
  - [ ] The two rules
- **Tests**: `ProjectRulesTest` on the fixtures (`// TODO #276 …` compliant, `// TODO later`
  reported); `mise run check`

### WP5: ruff ERA in ta-runner

- **Status**: open
- **Depends on**: none
- **Files**: `artifact/ta-runner/pyproject.toml`
- **Steps**:
  - [ ] Add `ERA`, delete what it reports
- **Tests**: `mise run runner-test`

## Tests

Each work package's tests. Before merging, a deliberate violation of each rule in a module outside
the pending blocks fails `mise run check` (the fixtures are that proof for `TAP-*`; for `ERA`, a
commented-out line in ta-runner, not committed).

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-checkstyle.md` "Javadoc", "Rules catalogue", "Suppressing a finding" | no Javadoc required or allowed outside `package-info.java` (`TAP-J1`), the form checks kept for it; `TAP-C1`, `TAP-C2`, with the `J` and `C` groups named in the id scheme; the `MissingJavadocType` scope line goes; one paragraph: until #276 is done, `checkstyle.xml` exempts the modules not cleaned yet from `TAP-J*` and `TAP-C*`, one block per plan |
| Text | `build-logic/checkstyle-rules/README.md` | the two checks in "The checks" |
| Text | `docs/coding-convention/repository-code-comments.md` "Javadoc", "How it is checked", new "Existing comments" | Javadoc: none, ports included, `package-info.java` aside; which rule checks what, and the list "What the rules cannot catch"; the triage of an existing comment: every Javadoc is removed, a why it held that passes the test becomes a `//` comment, other comments are kept when they say why and deleted otherwise |
| Text | `docs/coding-convention/backend-java-persistence.md` "The port promises" | the promise is stated in this document and proven by the adapter tests, not in a Javadoc on each write method |
| Text | `docs/coding-convention/README.md` table row of `repository-code-comments.md` | no Javadoc outside `package-info.java`; checked by Checkstyle `TAP-J1` / `TAP-C*`, ruff `ERA`, and the review |
| Text | `CLAUDE.md` | "Rules that the build checks": comments (`TAP-J1`, `TAP-C*`, ruff `ERA`), no Javadoc; "does not check" keeps the judgement part, without "Javadoc only where Checkstyle requires it, one sentence" |

## Out of scope

- The LLM steps and the doc's test and examples: `.plans/276-redundant-comment-agent-steps.md`.
- Cleaning up the existing comments: plans 3 to 9 (table above).
- TypeScript and Vue (ESLint), and ruff for `scripts/agent/` and `.claude/skills/` (developer: ta-runner
  only).
