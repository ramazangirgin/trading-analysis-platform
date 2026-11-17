# Repository: code comments

One rule for every language in the repository (Java, TypeScript, Vue, Python, SQL, shell, YAML): **a
comment is written only when it says what the code cannot.**

## The rule

The test: *would a reader lose something if this comment were deleted?* If not, delete it.

A comment says something the code, its names, its types and the docs do not: a reason that is not
obvious, a workaround, a race, a constraint from outside.

Never write one that repeats:

- a name, a signature, a type or an annotation;
- what a statement does;
- a convention that is documented elsewhere (naming, versioning, formatting, the schema layout);
- what the README or the API description already says.

To delete:

```java
/** Stores presets. */
public interface PresetRepositoryPort {
```

To keep (the code cannot show why):

```java
// No timeout: runs take minutes, and the stream ends with the run.
return new SseEmitter(0L);
```

Existing comments are no model. A comment that is already in the code around yours is not a reason
to write the same kind: when you touch it, remove it if it breaks the rule.

## Javadoc

There is none in backend Java, except in `package-info.java`. Ports (`core/inbound`,
`core/outbound`) are no exception. What becomes of a Javadoc that exists:

- a reason it held becomes a `//` comment on the code it explains;
- a `@throws` case is dropped: the exception type shows it;
- a fact that a convention document already states is deleted.

## File headers

No file header restates the schema, the naming rules or the Flyway versioning. The usage header of a
shell script under `scripts/` (what it does, its arguments) is the exception: it is what `--help`
cannot show.

## SQL

A migration has no comment on a table or a column that repeats its name, and no `COMMENT ON`
statement for it. A comment is kept only for a reason that is not obvious, such as a workaround.
A migration already merged into `main` is never edited to remove a comment: Flyway checksums it.

## How it is checked

| Rule | Tool | Reports | Scope |
|---|---|---|---|
| `TAP-J1` | Checkstyle | every Javadoc comment (`/**`) | main and test sources of every backend module; not `package-info.java` |
| `TAP-C1` | Checkstyle | a `//` comment on lines of its own whose words all occur in the next statement | main and test sources of every backend module |
| `TAP-C2` | Checkstyle | a `TODO` or `FIXME` without an issue number (`TODO #123: ...`) | main and test sources of every backend module |
| `ERA` | ruff | commented-out code | `artifact/ta-runner` |

See [backend-java-checkstyle.md](backend-java-checkstyle.md) for the rules, and the
[temporary filters (#276)](backend-java-checkstyle.md#temporary-filters-276) that keep the build
green until the existing Javadoc and comments are cleaned up.

What no tool catches, so the review agent and the developer's review of the pull request do:

- a restatement in other words, or one over several statements;
- a trailing comment or a block comment;
- an outdated comment;
- TypeScript, Vue, SQL, YAML and shell;
- a docstring, and the Python of `scripts/agent` and `.claude/skills`;
- Javadoc in `build-logic`, which Checkstyle does not check.
