# Plan: Build rules for comments and Javadoc

- **Issue**: #276 (Redundant comments: enforce, add LLM planning and review steps, document, clean up)
- **Plan**: 1 of 9 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The build checks what it can state precisely about comments: no Javadoc in backend Java (`TAP-J1`),
no `//` comment that only repeats the words of the next statement (`TAP-C1`), every `TODO` / `FIXME`
names an issue (`TAP-C2`), and no commented-out code in ta-runner (ruff `ERA`). The existing
Javadoc and comments are still there behind a temporary filter per cleanup plan (plans 3 to 9), so
the build stays green; `repository-code-comments.md` states the test, the examples, the exact scope
of the rules and what they cannot catch.

## Affected parts and their conventions

- Custom Checkstyle checks: [build-logic/checkstyle-rules/README.md](../build-logic/checkstyle-rules/README.md) (contract, tests, adding a check and a project rule).
- Backend: [backend-java-checkstyle.md](../docs/coding-convention/backend-java-checkstyle.md), [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md), [Changing a convention](../docs/coding-convention/README.md#changing-a-convention).
- ta-runner: [ta-runner-python-package-structure.md](../docs/coding-convention/ta-runner-python-package-structure.md) (ruff in `pyproject.toml`).

## Design

Decisions taken by the developer (not open):

- **No Javadoc anywhere in backend Java, ports (`core/inbound`, `core/outbound`) included**;
  `package-info.java` stays outside the rule. One rule bans it; there is no "one sentence" or
  "no `@param`" rule. A reason a Javadoc held becomes a `//` why-comment; a `@throws` case is dropped
  (the exception type shows it). Orchestration `<feature>/inbound` use cases are not ports either.
- **Enforcement first, cleanup later**: the documented rule "a project rule goes in with its
  findings fixed in the same pull request" is set aside for #276 (about 560 Javadoc blocks in about
  360 files), see "Temporary filters" below.
- **Python and shell**: ruff `ERA` for ta-runner only; no step for the Python of `scripts/agent` or
  `.claude/skills`, no docstring rule, no shell rule (no linter here has one).

The rules (ids: group `J` Javadoc, `C` comments, both already reserved in the id scheme):

- **`TAP-J1`**, new generic check `ForbiddenJavadocCheck`: reports every Javadoc comment (a block
  comment opening with `/**`, not the empty `/**/`), wherever it is, on its first line. Parameter:
  `suggestion`. Main and test sources of every module; a `SuppressionSingleFilter` on
  `package-info\.java$` is its scope (not a suppression), like `TAP-D7`'s.
- **`TAP-C1`**, new generic check `CommentRepeatsCodeCheck`: a `//` comment on lines of its own
  (consecutive such lines are one comment) is reported, on its first line, when it has at least one
  word and every word (letters only, lower-cased, minus `ignoredWords`) occurs among the words of
  the next statement: its identifiers split at camel case, `_` and digits, and its keywords, from
  the first token after the comment to the first `;` or `{`. Parameters: `ignoredWords`,
  `suggestion`. `TAP-C1` sets `ignoredWords = a, an, the, and, or, of, to, in, on, at, for, with, by,
  from, as, is, are, be, it, its, this, that, then`. A trailing comment and a block comment are not
  checked. A fixture's reported comment carries the `// violation` marker on its line, so the
  statement after it names `violation` too.
- **`TAP-C2`**, built-in `TodoComment` with `format = \b(TODO|FIXME)\b(?!.*#[0-9]+)` and a
  `todo.match` message naming the rule (`TODO #123: ...`).
- Rejected from the issue's list: an allow-list of `//` markers (it would ban the why-comments the
  rule keeps; `TAP-C1` and the review cover the rest).

Javadoc checks in `checkstyle.xml`: `MissingJavadocType` (id `MissingJavadocPortType`) and its
scope filter go, and `JavadocMethod` goes (no method Javadoc remains). `SummaryJavadoc`,
`JavadocMissingLeadingAsterisk`, `InvalidJavadocPosition`, `NonEmptyAtclauseDescription`,
`AtclauseOrder` and `SingleLineJavadoc` stay: they still check the Javadoc of `package-info.java`.

**Temporary filters.** One block per cleanup plan, below the scope filters in `checkstyle.xml`,
headed by an XML comment `Temporary (#276, plan <n>): removed by .plans/276-<slug>.md`, each block a
`SuppressionSingleFilter` per rule id that reports findings in its modules today (`TAP-J1`
everywhere; `TAP-C1` only where it reports, expected: the root module's tests). `files` patterns
start with `artifact[\\/]backend[\\/]`, so `ProjectRulesTest`'s fixtures (copied to a temporary
folder) are never filtered.

| Plan | Slug | Modules (`artifact/backend/...`) |
|---|---|---|
| 3 | `comments-analysis-core` | `domain/analysis/core` |
| 4 | `comments-analysis-adapter-orchestration` | `domain/analysis/adapter`, `orchestration` |
| 5 | `comments-identity` | `domain/identity` |
| 6 | `comments-report-catalog` | `domain/report`, `domain/catalog` |
| 7 | `comments-settings-library` | `domain/settings`, `library` |
| 8 | `comments-bff` | `bff` |
| 9 | `comments-backend-root` | the root module (`artifact/backend/src`) |

## Work packages

Paths under `build-logic/checkstyle-rules/` are relative to it.

### WP1: Check `ForbiddenJavadocCheck`

- **Status**: open
- **Depends on**: none
- **Files**: `src/main/java/tr/girgin/checkstyle/ForbiddenJavadocCheck.java`, `src/main/resources/tr/girgin/checkstyle/messages.properties`, `src/test/java/tr/girgin/checkstyle/ForbiddenJavadocCheckTest.java`, `src/test/resources/checks/ForbiddenJavadocCheck/`, `docs/ForbiddenJavadocCheck.md`, `README.md`
- **Steps**:
  - [ ] Write the check, its reference document and its row in the README's table of checks
- **Tests**: `ForbiddenJavadocCheckTest` (a Javadoc on a type, a member, a record component, a
  misplaced one inside a method; near misses `/**/`, `/* */`, `//` and `/**` in a string literal;
  the message with and without `suggestion`), `DocumentationTest`, 100% coverage.

### WP2: Check `CommentRepeatsCodeCheck`

- **Status**: open
- **Depends on**: WP1
- **Files**: `src/main/java/tr/girgin/checkstyle/CommentRepeatsCodeCheck.java`, `src/main/resources/tr/girgin/checkstyle/messages.properties`, `src/test/java/tr/girgin/checkstyle/CommentRepeatsCodeCheckTest.java`, `src/test/resources/checks/CommentRepeatsCodeCheck/`, `docs/CommentRepeatsCodeCheck.md`, `README.md`
- **Steps**:
  - [ ] Write the check, its reference document and its row in the README's table of checks
- **Tests**: `CommentRepeatsCodeCheckTest`: reported (one word, several, a multi-line comment,
  camel case and `_` names, a keyword, an annotated declaration); not reported (a word missing from
  the statement, an empty or symbols-only comment, a trailing comment, a block comment, a comment
  before `}`, a word only in `ignoredWords`); invalid `ignoredWords`; the message with and without
  `suggestion`. `DocumentationTest`, 100% coverage.

### WP3: Project rules `TAP-J1`, `TAP-C1`, `TAP-C2` and the temporary filters

- **Status**: open
- **Depends on**: WP1, WP2
- **Files**: `config/checkstyle/checkstyle.xml`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-J1/`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-C1/`, `build-logic/checkstyle-rules/src/test/resources/rules/TAP-C2/`
- **Steps**:
  - [ ] Configure the three rules at the end of `TreeWalker`, the `TAP-J1` scope filter, the
    Javadoc checks as decided, and the temporary filter blocks
  - [ ] Fixtures: `Violation.java` and `Compliant.java` per rule, and a `package-info.java` with
    a Javadoc under `TAP-J1` (reports nothing)
- **Tests**: `ProjectRulesTest`, `DocumentationTest` (catalogue rows), and `mise run build` green:
  the temporary filters cover every finding of today, and nothing else is filtered.

### WP4: ruff `ERA` for ta-runner

- **Status**: open
- **Depends on**: none
- **Files**: `artifact/ta-runner/pyproject.toml`
- **Steps**:
  - [ ] Add `ERA` to `[tool.ruff.lint] select`
- **Tests**: `mise run runner-test` (no finding today); a commented-out line such as `# x = 1`
  in a scratch copy is reported, and not committed.

## Tests

Each work package's tests. The rules failing on deliberate violations are the fixtures of WP1 to
WP3.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/repository-code-comments.md` | Rewrite: the test "would a reader lose something if this comment were deleted?"; a comment to delete (`/** Stores presets. */` on `PresetRepositoryPort`) and one to keep (`// No timeout: runs take minutes, and the stream ends with the run.` above `new SseEmitter(0L)` in `AnalysesApiDelegateImpl`); "Javadoc": none in backend Java except `package-info.java`, and what becomes of one (reason to a `//` comment, `@throws` dropped, a fact a convention document states deleted); "How it is checked": the exact scope of `TAP-J1`, `TAP-C1`, `TAP-C2` and ruff `ERA` (sources, modules, what is not checked), what they cannot catch (a restatement in other words, a trailing or block comment, a comment over several statements, an outdated comment, TypeScript, Vue, SQL, YAML, shell, docstrings, the Python of `scripts/agent` and `.claude/skills`, Javadoc in `build-logic`), the link to the temporary filters |
| Text | `docs/coding-convention/backend-java-checkstyle.md` | Intro and "Javadoc" section as decided; groups `C` and `J` named in the id scheme; catalogue rows `TAP-J1`, `TAP-C1`, `TAP-C2` (with WP3: `DocumentationTest` checks them); the scope list ("Six settings": `MissingJavadocType` out, `TAP-J1`'s `package-info.java` in); a paragraph "Temporary filters (#276)" under "Suppressing a finding" with the table of the Design and why the rule went in before its findings were fixed |
| Text | `docs/coding-convention/README.md` | Rows of `backend-java-checkstyle.md` and `repository-code-comments.md` (checked by Checkstyle, ruff and review, no Javadoc); the ta-runner ruff row names `ERA` |
| Text | `CLAUDE.md` | The automated part moves to "Rules that the build checks" (`TAP-J1`, `TAP-C1`, `TAP-C2`, ruff `ERA`); "Rules that the build does not check" keeps "no comment that restates the code" with the test, without the Javadoc sentence |
| Text | `docs/coding-convention/backend-java-persistence.md` "The port promises" | "(Javadoc on each write method)" becomes the rule stated there, proved by each adapter's stale-write test |

## Out of scope

- The cleanup of the existing Javadoc and comments: plans 3 to 9.
- The agents' prompts and the planning skill (`implement.md`, `review.md`, `fix.md`,
  `.claude/skills/plan-from-issue/`): plan 2. Until it is merged, `implement.md` still says
  "Javadoc only where Checkstyle requires it"; Checkstyle then rejects one.
- Javadoc in `build-logic` (Checkstyle does not check that build, see its README) and comments in
  the frontend: no rule; the review covers them.

## Open questions

- `TAP-C1` reports the four section headings of the root module's tests whose words are all in the
  next member's name (`// ---- list and get ----` above `listAndGetShowRolesStatusLockAndLastLogin`,
  `// --- BFF ---` above `bff_api_does_not_use_domain_models`), while the headings with other
  words pass. Plan 9 deletes or rewrites them. Keep that, or exempt a heading (words between runs
  of `-`) from the rule, so that a test class keeps its groups?
