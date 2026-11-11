# Plan: Checkstyle and ruff rules for redundant comments

- **Issue**: #276 (Redundant comments: enforce, add LLM planning and review steps, document, clean up)
- **Plan**: 1 of 9 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The build fails on the comments a tool can judge without guessing: a Javadoc outside the ports, a
Javadoc on a non-public declaration of a port, a Javadoc of more than one sentence or with `@param` /
`@return`, a comment whose words all appear in the statement below it, a `TODO` without an issue
number, and commented-out code in ta-runner. Every backend module whose existing comments plans 3 to 9
clean up is exempt from the new rules through a temporary filter, one per cleanup plan, which that
plan removes; a new module, and every module once cleaned, is checked at once.

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
| Javadoc only where Checkstyle requires it | Precise. Required is `MissingJavadocPortType` (public types in `core/inbound`, `core/outbound`). `TAP-J1`: no Javadoc outside the port files; `TAP-J2`: in port files, none on a declaration whose effective scope is not public. `package-info.java` keeps its Javadoc ([package structure](../docs/coding-convention/backend-java-package-structure.md)) |
| A required Javadoc is one sentence, no `@param` / `@return` | Precise with the sentence definition below: `TAP-J3`. `@throws` stays allowed (`JavadocMethod` checks it): it is where an error case goes |
| `//` only for an allow-list of markers | Not adopted: a `//` comment is where a why goes (also the why of a Javadoc that `TAP-J1` removes), so a ban would remove the comments the rule wants. `TAP-C2` covers the `TODO` marker |
| `TODO` with an issue number | Precise: `TAP-C2`, built-in [`TodoComment`](https://checkstyle.org/checks/misc/todocomment.html), `format` `\b(TODO|FIXME)\b(?!:?\s*\(?#\d+)`, message starting with the id |
| A comment that repeats the next line | Precise enough as a heuristic that reports only when every word is in the code: `TAP-C1` |
| Python | ruff `ERA` (commented-out code) in ta-runner. No ruff rule forbids or limits a docstring (pydocstyle checks the form, and its `D1` rules require docstrings), so docstrings stay with the review |
| Shell | No tool of the repository checks shell comments, and none has such a rule: review only |

Port files are the paths the `MissingJavadocPortType` filter already selects
(`src/main/**/core/(inbound|outbound)/**`). Orchestration's `<feature>/inbound` use cases are not
ports for these rules (Open questions).

### New checks (`tr.girgin.checkstyle`, generic as the module's contract asks)

- `ForbiddenJavadocCheck`: reports a Javadoc on a declaration of the configured tokens whose
  effective scope (its modifier, interface and annotation members public, narrowed by the enclosing
  types) is below `allowedScope` (Checkstyle's `Scope` names; `nothing`, the default, reports every
  Javadoc). Finds the Javadoc of a declaration as `InvalidJavadocPosition` does (comment nodes,
  `JavadocUtil.isJavadocComment`).
- `JavadocContentCheck`: reports a Javadoc whose description (the text before the first block tag)
  holds more than `maxSentences` sentences (default: no limit), and one with a block tag listed in
  `forbiddenTags` (default none); one message each. A sentence ends at `.`, `!` or `?` followed by
  whitespace or the end of the description; text inside inline tags (`{@code a.b}`) and HTML tags does
  not count, `<p>` ends a sentence, and the entries of `abbreviations` (default `e.g.`, `i.e.`) do not.
- `CommentRepeatsCodeCheck`: takes a comment that stands on lines of its own (consecutive `//` lines,
  or a `/* */` that is not Javadoc) and the statement or declaration that starts on the next line of
  code. Words: letters and digits, lower case; identifiers and keywords of the code split at camel
  case, digits and underscores; a trailing `s` dropped on both sides; the words in `ignoredWords`
  (default: English function words such as `a`, `an`, `the`, `of`, `to`, `is`, `and`, `or`, `for`,
  `in`, `on`, `with`, `this`, `it`) are dropped from the comment. Reports, on the line of that
  statement, when a word is left and each one occurs in the code. A comment at the end of a code
  line (a suppression's reason) is not checked. Reporting on the statement's line lets a fixture
  mark it with `// violation`.

Every check takes `suggestion`, as the others do.

### Configuration (`config/checkstyle/checkstyle.xml`)

- `TAP-J1`: `ForbiddenJavadocCheck`, every declaration token except `PACKAGE_DEF`; a
  `SuppressionSingleFilter` turns it off in port files.
- `TAP-J2`: `ForbiddenJavadocCheck`, `allowedScope = public`; a filter limits it to port files.
- `TAP-J3`: `JavadocContentCheck`, `maxSentences = 1`, `forbiddenTags = param, return`, every token
  except `PACKAGE_DEF`, everywhere (after `TAP-J1` and `TAP-J2` that is the ports' public
  declarations).
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

`ProjectRulesTest` gets an optional `rules/<id>/package` file: the folder under `src/<set>/java` the
fixtures are copied into, so the `TAP-J2` fixtures can sit in a `x/core/inbound` path. The fixture
paths contain no `artifact/backend`, so the pending filters do not hide them.

`artifact/ta-runner/pyproject.toml` adds `ERA` to ruff's `select`.

### What the rules cannot catch

- a one-sentence port Javadoc that only repeats the name or the signature (`Port for users.`);
- a comment that says what the code says in other words (`persist` for `save`), repeats more than the
  next statement, a type or a documented convention, or sits at the end of a line;
- a comment that is wrong or outdated (the issue's example: a strict format the constructor no
  longer checks), and a why nobody needs;
- Python docstrings and comments other than commented-out code; shell, TypeScript, Vue, SQL and YAML
  comments; the custom checks module's own sources (Checkstyle does not check them).

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
  - [ ] Effective scope of nested and interface members, every `allowedScope` value, an invalid value
- **Tests**: `ForbiddenJavadocCheckTest`: every scope and token, a block comment and a `//` comment
  that are not reported, the message with and without `suggestion`; 100% coverage

### WP2: JavadocContentCheck

- **Status**: open
- **Depends on**: WP1
- **Files**: `build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/JavadocContentCheck.java`,
  `build-logic/checkstyle-rules/src/main/resources/tr/girgin/checkstyle/messages.properties`,
  `build-logic/checkstyle-rules/src/test/java/tr/girgin/checkstyle/JavadocContentCheckTest.java`,
  `build-logic/checkstyle-rules/src/test/resources/checks/JavadocContentCheck/`,
  `build-logic/checkstyle-rules/docs/JavadocContentCheck.md`, `build-logic/checkstyle-rules/README.md`
- **Steps**:
  - [ ] Sentence counting as in Design, the two findings
- **Tests**: `JavadocContentCheckTest`: one and two sentences, a dot inside `{@code}`, `<p>`, an
  abbreviation, `?` and `!`, a block tag after the description, each forbidden tag, `@throws` not
  reported; 100% coverage

### WP3: CommentRepeatsCodeCheck

- **Status**: open
- **Depends on**: WP2
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

### WP4: Javadoc rules TAP-J1 to TAP-J3

- **Status**: open
- **Depends on**: WP1, WP2
- **Files**: `config/checkstyle/checkstyle.xml`,
  `build-logic/checkstyle-rules/src/test/java/tr/girgin/checkstyle/ProjectRulesTest.java`,
  `build-logic/checkstyle-rules/src/test/resources/rules/TAP-J1/`,
  `build-logic/checkstyle-rules/src/test/resources/rules/TAP-J2/`,
  `build-logic/checkstyle-rules/src/test/resources/rules/TAP-J3/`,
  `build-logic/checkstyle-rules/README.md`, `docs/coding-convention/backend-java-checkstyle.md`
- **Steps**:
  - [ ] The three rules, their scope filters and the seven pending blocks
  - [ ] The `package` fixture file in `ProjectRulesTest`
- **Tests**: `ProjectRulesTest` on the fixtures (a Javadoc on a private method of a port and on a
  service reported; the public port's one-sentence Javadoc and a `@throws` not); `mise run check`
  passes on main's code

### WP5: Comment rules TAP-C1 and TAP-C2

- **Status**: open
- **Depends on**: WP3, WP4
- **Files**: `config/checkstyle/checkstyle.xml`,
  `build-logic/checkstyle-rules/src/test/resources/rules/TAP-C1/`,
  `build-logic/checkstyle-rules/src/test/resources/rules/TAP-C2/`,
  `docs/coding-convention/backend-java-checkstyle.md`
- **Steps**:
  - [ ] The two rules
- **Tests**: `ProjectRulesTest` on the fixtures (`// TODO #276 …` compliant, `// TODO later`
  reported); `mise run check`

### WP6: ruff ERA in ta-runner

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
| Text | `docs/coding-convention/backend-java-checkstyle.md` "Javadoc", "Rules catalogue", "Suppressing a finding" | the five rules with the `J` and `C` groups named in the id scheme; the scope filters of `TAP-J1` / `TAP-J2`; one paragraph: until #276 is done, `checkstyle.xml` exempts the modules not cleaned yet from `TAP-J*` and `TAP-C*`, one block per plan |
| Text | `build-logic/checkstyle-rules/README.md` | the three checks in "The checks"; `rules/<id>/package` under "Tests" |
| Text | `docs/coding-convention/repository-code-comments.md` "Javadoc", "How it is checked", new "Existing comments" | Javadoc: one sentence, `@throws` for an error, no `@param` / `@return`, nowhere else than ports and `package-info.java`; which rule checks what, and the list "What the rules cannot catch"; the triage of an existing comment: required Javadoc shortened to one sentence (an error case to `@throws`), a why kept (outside a port as a `//` comment above the declaration), the rest deleted |
| Text | `docs/coding-convention/README.md` table row of `repository-code-comments.md` | "checked by review, not by a tool" becomes Checkstyle `TAP-J*` / `TAP-C*`, ruff `ERA`, and the review |
| Text | `CLAUDE.md` | "Rules that the build checks": comments (`TAP-J*`, `TAP-C*`, ruff `ERA`); "does not check" keeps the judgement part |

## Out of scope

- The LLM steps and the doc's test and examples: `.plans/276-redundant-comment-agent-steps.md`.
- Cleaning up the existing comments: plans 3 to 9 (table above).
- TypeScript and Vue (ESLint), and ruff for `scripts/agent/` and `.claude/skills/` (no ruff run there
  today): Open questions.

## Open questions

- Should orchestration's `<feature>/inbound` use cases count as ports (Javadoc allowed and required,
  as in `core/inbound`)? The plan follows the current rule: they are not, so `TAP-J1` removes their
  Javadoc (plan 4), `@throws` included.
- `package-info.java` stays outside `TAP-J3` (several sentences on a module's role). Should it be one
  sentence as well?
- About 37 port Javadocs hold a second sentence a caller needs (`… fails with X`, what `null`
  means). With `TAP-J3` it goes into a `@throws` where the method throws an exception, otherwise
  into the one sentence. Is a long sentence acceptable there, or should `maxSentences` be 2?
- Add `ERA` for the agent scripts' Python too (a new `uv run --with ruff ruff check --select ERA
  scripts/agent .claude/skills` step in `mise run check`)?
