# Plan: Project Checkstyle rules, part 5: Java API usage and comments (J, S)

- **Issue**: #55 (Java: project-specific Checkstyle rules (custom checks))
- **Plan**: 5 of 7 for this issue; depends on: `55-checkstyle-rules-module`
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The build fails when `Optional` is used as a field, parameter or record component, when
`.collect(Collectors.toList())` is used instead of `Stream.toList()`, when a list is copied the
pre-Java-10 way, when a `@SuppressWarnings` has no reason comment, when a `TODO` / `FIXME` does not
name an issue, and when a `//` comment holds commented-out code. Each finding carries a `TAP-J*` /
`TAP-S*` id. Three new generic checks carry J1 to J3 and S1; S2 and S3 use Checkstyle's built-in
checks, which are generic already.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Custom checks module, Checkstyle config, docs | The contract from plan 1: generic checks with documented parameters, a reference per check, index and catalogue rows, `ProjectRulesTest` fixtures per rule, 100% coverage |
| Backend (`artifact/backend`) | `backend-java-checkstyle.md` "Suppressing a finding": the reason on the same or the previous line, which S1 now enforces |

## Design

### `ForbiddenTypeInDeclarationCheck` (new)

| Parameter | Type | Default | Meaning |
|---|---|---|---|
| `types` | list of type names | (required) | Types not allowed, simple or qualified (resolved through imports); matches the outer type of a generic (`Optional<String>`) and, with `includeTypeArguments`, type arguments too |
| `declarations` | list of `FIELD`, `PARAMETER`, `CONSTRUCTOR_PARAMETER`, `LAMBDA_PARAMETER`, `RECORD_COMPONENT`, `RETURN`, `LOCAL_VARIABLE` | (required) | Where the type is not allowed |
| `includeTypeArguments` | boolean | `false` | Also report `List<Optional<X>>` |
| `suggestion` | string | empty | Appended to the message |

### `ForbiddenNestedCallCheck` (new)

Reports a call (or constructor call) whose argument is another given call.

| Parameter | Type | Default | Meaning |
|---|---|---|---|
| `outer` | list of member patterns (plan 1 syntax) | (required) | The enclosing call (`*#collect(1)`, `ArrayList#new(1)`) |
| `inner` | list of member patterns | (required) | The call used as its argument (`Collectors#toList(0)`, `Arrays#asList()`, `*#new()`) |
| `directArgumentOnly` | boolean | `true` | Only when the inner call is itself an argument of the outer one, not nested deeper (so `collect(groupingBy(k, toList()))` is not reported) |
| `suggestion` | string | empty | Appended to the message |

### `AnnotationCommentCheck` (new)

Requires a comment next to given annotations.

| Parameter | Type | Default | Meaning |
|---|---|---|---|
| `annotations` | list of type names | (required) | Annotations that need a comment (`SuppressWarnings`) |
| `valuePattern` | regex | `.*` | Only uses whose value matches (`^checkstyle:.*` would limit S1 to Checkstyle suppressions) |
| `placement` | `SAME_LINE_OR_PREVIOUS`, `SAME_LINE`, `PREVIOUS_LINE` | `SAME_LINE_OR_PREVIOUS` | Where the comment must be: a `//` or `/* */` comment at the end of the annotation's last line, or on the line directly above (Javadoc does not count) |
| `minLength` | int | `3` | A comment shorter than this does not count |
| `suggestion` | string | empty | Appended to the message |

It reads comments from the syntax tree (`isCommentNodesRequired`), so annotations spread over
several lines and annotations following other annotations are handled.

### Rules

| Id | Rule | Check and parameters | Sources | Hits today |
|---|---|---|---|---|
| TAP-J1 | `Optional` only as a return type (and for locals): never a field, parameter or record component | `ForbiddenTypeInDeclarationCheck`: `types = java.util.Optional`, `declarations = FIELD, PARAMETER, CONSTRUCTOR_PARAMETER, RECORD_COMPONENT` | main and test | 0 |
| TAP-J2 | `stream.toList()` instead of `.collect(Collectors.toList())` / `toUnmodifiableList()` | `ForbiddenNestedCallCheck`: `outer = *#collect(1)`, `inner = Collectors#toList(0), Collectors#toUnmodifiableList(0)`. `groupingBy(..., Collectors.toList())` in `ReportService` has no `Stream.toList()` equivalent and is not matched | main and test | 0 |
| TAP-J3 | `List.of` / `List.copyOf` instead of `new ArrayList<>(Arrays.asList(...))` and `Collections.unmodifiableList(new ...)`. Lists only: `Map.copyOf` does not keep insertion order, so `Collections.unmodifiableMap(new LinkedHashMap<>(...))` (`RunEvent`, `Report`) stays allowed | two `ForbiddenNestedCallCheck` instances under one id: `outer = ArrayList#new(1)`, `inner = Arrays#asList()`; and `outer = Collections#unmodifiableList(1)`, `inner = *#new()` | main and test | 0 |
| TAP-S1 | Every `@SuppressWarnings` says why, at the end of its line or on the line above | `AnnotationCommentCheck`: `annotations = SuppressWarnings` | main and test | 0 expected; checked first (`AnalysesApiDelegateImpl`'s class-level suppression) |
| TAP-S2 | `TODO` / `FIXME` names an issue: `TODO(#46): ...` | built-in `TodoComment`, `format = \b(TODO\|FIXME)\b(?!\(#\d+\))` | main and test | 0 |
| TAP-S3 | No commented-out code: a `//` comment line ending in `;` | built-in `RegexpSingleline` (Checker level, `fileExtensions = java`), `^\s*//.*;\s*$` | main and test | 0 |

`// CHECKSTYLE:OFF` from the issue's S1 is not covered: no `SuppressionCommentFilter` is configured,
so such a comment has no effect. The doc says so.

Dropped from the issue: **J4** (no `==` on strings beyond literals). Checkstyle has no type
information, so it cannot tell `String == String` from `int == int`; the built-in
`StringLiteralEquality` covers literals, the compiler and IntelliJ the rest.

## Work packages

### WP1: `ForbiddenTypeInDeclarationCheck`

- **Depends on**: none (plan 1 merged)
- **Files**: `build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/ForbiddenTypeInDeclarationCheck.java`,
  `messages.properties`, its test class, `src/test/resources/checks/ForbiddenTypeInDeclarationCheck/*`
- **Tests**: every declaration kind reported and not reported; generics with and without
  `includeTypeArguments`; arrays; qualified use; an unrelated `Optional` type without import;
  lambda parameters with and without a declared type; invalid declaration names. 100% coverage.

### WP2: `ForbiddenNestedCallCheck`

- **Depends on**: none
- **Files**: `ForbiddenNestedCallCheck.java`, `messages.properties`, its test class and fixtures
- **Tests**: direct argument and deeper nesting with `directArgumentOnly` on and off; constructor
  calls as outer and inner; argument counts; a chained receiver (`list.stream().collect(...)`);
  wildcard receiver; a matching inner call outside any outer call (not reported). 100% coverage.

### WP3: `AnnotationCommentCheck`

- **Depends on**: none
- **Files**: `AnnotationCommentCheck.java`, `messages.properties`, its test class and fixtures
- **Tests**: each placement; a block comment; Javadoc above (does not count); a comment two lines
  above (does not count); a multi-line annotation with the comment after its last line; the
  annotation after another annotation; `valuePattern` matching and not matching; `minLength`.
  100% coverage.

### WP4: Rules J1 to J3, S1 to S3

- **Depends on**: WP1 to WP3
- **Files**: `config/checkstyle/checkstyle.xml`, `src/test/resources/rules/TAP-J1/*` ... `TAP-S3/*`,
  any backend file with a finding (none expected)
- **Steps**:
  - [ ] Add the instances; fixtures for `ProjectRulesTest`, with the near misses as compliant code
        (a local `Optional`, `groupingBy(k, Collectors.toList())`,
        `Collections.unmodifiableMap(new LinkedHashMap<>(m))`, both comment placements, `TODO(#46)`,
        a `//` comment ending in a period).
  - [ ] Backend Checkstyle clean.
- **Tests**: `ProjectRulesTest`.

### WP5: Documentation

- **Depends on**: WP4
- **Files**: `docs/coding-convention/backend-java-checkstyle-checks/ForbiddenTypeInDeclarationCheck.md`,
  `ForbiddenNestedCallCheck.md`, `AnnotationCommentCheck.md` (new),
  `docs/coding-convention/backend-java-checkstyle-custom-checks.md`,
  `docs/coding-convention/backend-java-checkstyle.md`
- **Steps**:
  - [ ] Reference documents from the template, each with configuration examples for at least two
        conditions (J1 and e.g. no `java.util.Date` fields; J2 and e.g.
        `Optional.ofNullable(map.get(k))`; S1 and e.g. `@Disabled` needs a comment).
  - [ ] Index rows; catalogue rows J1 to J3, S1 to S3 (S2 and S3 link the built-in checks' pages).
  - [ ] "Suppressing a finding": the reason comment is enforced (`TAP-S1`); `// CHECKSTYLE:OFF`
        has no effect. J4 dropped, with the reason.
- **Tests**: `DocumentationTest`.

## Tests

`mise run build`: the three checks' unit tests at 100% coverage, `ProjectRulesTest` with the J and S
fixtures, `DocumentationTest`, backend Checkstyle clean.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-checkstyle-checks/` | Three new reference documents |
| Text | `docs/coding-convention/backend-java-checkstyle-custom-checks.md` | Three index rows |
| Text | `docs/coding-convention/backend-java-checkstyle.md` | Catalogue rows J and S; "Suppressing a finding"; J4 dropped |
| Screenshot | none | No UI change |

## Out of scope

- Other groups: plans 2 to 4, 6, 7.
