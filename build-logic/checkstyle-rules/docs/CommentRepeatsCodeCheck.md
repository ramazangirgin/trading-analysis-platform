# `CommentRepeatsCodeCheck`

Reports a `//` comment on lines of its own whose words all occur in the code that follows it. Source:
[`CommentRepeatsCodeCheck.java`](../src/main/java/tr/girgin/checkstyle/CommentRepeatsCodeCheck.java).
Overview of all checks: [custom checks](../README.md).

## What it matches

- **A comment** is one or more consecutive `//` lines with nothing before the `//` on their line. The
  lines are one comment; it is reported on its first line. A trailing comment (`int size; // size`) and a
  block comment (`/* ... */`, Javadoc included) are not checked.
- **Its words** are its runs of letters, lower-cased, without the `ignoredWords`. A comment without any
  word (empty, `// ----`, only numbers or only ignored words) is not reported.
- **The next statement** runs from the first token after the comment to the first `;` or `{`. Comments
  in between are skipped, and so are the contents of string literals, text blocks and character
  literals. A comment before a `}` has no next statement and is not reported. Annotations before a
  declaration belong to its statement.
- **Its words** are its identifiers split at camel case, `_` and digits (`MAX_RETRY_COUNT`, `maxRetry2Count`),
  and its keywords (`return`, `void`), lower-cased.
- **Reported** when every word of the comment is a word of the statement. A comment that adds one word
  the code does not show is not reported, however small the addition.
- It sees words, not meaning: a restatement in other words, a comment over several statements and an
  outdated comment are not found.

## Parameters

Set in `checkstyle.xml` as `<property name="..." value="..."/>`. `id` and `severity` are the same for
every module and not listed.

| Parameter | Type | Default | Required | Meaning |
|---|---|---|---|---|
| `ignoredWords` | list of words | empty | no | Words of a comment that need not occur in the code, such as articles: `a, an, the`. Letters only, case-insensitive |
| `suggestion` | string | empty | no | Appended to the message: how to fix the finding |

## Configuration examples

### Example 1: ignore articles and prepositions

```xml
<module name="tr.girgin.checkstyle.CommentRepeatsCodeCheck">
    <property name="id" value="TAP-X1"/>
    <property name="ignoredWords" value="a, an, the, of"/>
    <property name="suggestion" value="Say why, or delete the comment."/>
</module>
```

```java
// Set the name of a user.                // reported: "set", "name", "user" are all in the statement
void setNameOfUser(String name) {}

// Max retry count                        // reported
static final int MAX_RETRY_COUNT = 3;

// Sets the name.                         // not reported: "sets" is not "set"
void setName(String name) {}

// Retry: the call is idempotent.         // not reported: "idempotent" is not in the statement
void retry() {}

int size; // size                         // not reported: a trailing comment
```

### Example 2: no ignored words

```xml
<module name="tr.girgin.checkstyle.CommentRepeatsCodeCheck"/>
```

```java
// the name                               // not reported: "the" is not in the statement
void name() {}

// return value                           // reported
return value;
```

## Message

`<id>: Comment only repeats the words of the code below it.` followed by the `suggestion`, if there is
one. `<id>` and its colon are left out when the module has no `id`.

| Finding | Message |
|---|---|
| Comment | `TAP-X1: Comment only repeats the words of the code below it. Say why, or delete the comment.` |

## Project rules that use it

| Rule | Configuration |
|---|---|
| [`TAP-C1`](../../../docs/coding-convention/backend-java-checkstyle.md#project-rules) | `ignoredWords` of articles, prepositions and pronouns, and a `suggestion` |
