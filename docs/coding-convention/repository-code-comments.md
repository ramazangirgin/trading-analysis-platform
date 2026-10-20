# Repository: code comments

One rule for every language in the repository (Java, TypeScript, Vue, Python, SQL, shell, YAML): **a
comment is written only when it says what the code cannot.**

## The rule

A comment or Javadoc says something the code, its names, its types and the docs do not: a reason
that is not obvious, a workaround, a race, a constraint from outside.

Never write one that repeats:

- a name, a signature, a type or an annotation;
- what a statement does;
- a convention that is documented elsewhere (naming, versioning, formatting, the schema layout);
- what the README or the API description already says.

Existing comments are no model. A comment that is already in the code around yours is not a reason
to write the same kind: when you touch it, remove it if it breaks the rule.

## Javadoc

Javadoc is written only where Checkstyle requires it (public types in `core/inbound` and
`core/outbound`, see [backend-java-checkstyle.md](backend-java-checkstyle.md)): one summary
sentence, then only what a caller must know (the errors thrown, an ordering, what `null` or empty
means).

## File headers

No file header restates the schema, the naming rules or the Flyway versioning. The usage header of a
shell script under `scripts/` (what it does, its arguments) is the exception: it is what `--help`
cannot show.

## SQL

A migration has no comment on a table or a column that repeats its name, and no `COMMENT ON`
statement for it. A comment is kept only for a reason that is not obvious, such as a workaround.
A migration already merged into `main` is never edited to remove a comment: Flyway checksums it.

## How it is checked

No tool can tell a redundant comment from a useful one. The review agent reports every redundant
comment as a finding, also one copied from existing code, and the developer's review of the pull
request is the backstop. This is the documented exception to the rule that a convention is
enforced by a tool (see [Changing a convention](README.md#changing-a-convention)).
