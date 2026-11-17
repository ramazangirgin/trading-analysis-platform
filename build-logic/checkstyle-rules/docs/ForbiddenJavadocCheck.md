# `ForbiddenJavadocCheck`

Reports every Javadoc comment, wherever it is. Source:
[`ForbiddenJavadocCheck.java`](../src/main/java/tr/girgin/checkstyle/ForbiddenJavadocCheck.java).
Overview of all checks: [custom checks](../README.md).

## What it matches

- **A Javadoc** is a block comment that opens with `/**`: on a type, a member, a record component, or
  anywhere else, such as inside a method body, where it is not a Javadoc for the compiler but still one
  by its form. `/***/` is one too.
- **Not reported:** the empty block comment `/**/`, a plain block comment `/* ... */`, a `//` comment, and
  `/**` inside a string literal or a text block (it is not a comment).
- **Where it reports:** on the line where the comment opens.
- The check has no scope of its own. A file that may keep its Javadoc (`package-info.java`) is left out
  with a suppression filter in the configuration.

## Parameters

Set in `checkstyle.xml` as `<property name="..." value="..."/>`. `id` and `severity` are the same for
every module and not listed.

| Parameter | Type | Default | Required | Meaning |
|---|---|---|---|---|
| `suggestion` | string | empty | no | Appended to the message: how to fix the finding |

## Configuration examples

### Example 1: no Javadoc, with advice

```xml
<module name="tr.girgin.checkstyle.ForbiddenJavadocCheck">
    <property name="id" value="TAP-X1"/>
    <property name="suggestion" value="Delete it, or write a // comment that says why."/>
</module>
```

```java
/** Stores presets. */                // reported
interface PresetRepositoryPort {

    /**                               // reported
     * Finds a preset.
     */
    Preset find(long id);

    /**/                              // not reported: an empty block comment
    /* Plain block comment. */        // not reported
    // A line comment.                // not reported
}
```

### Example 2: no Javadoc, without advice

```xml
<module name="tr.girgin.checkstyle.ForbiddenJavadocCheck"/>
```

```java
void work() {
    /** Misplaced. */                 // reported
    int size = 3;
    String text = "/** a string */";  // not reported: not a comment
}
```

## Message

`<id>: Javadoc is not allowed.` followed by the `suggestion`, if there is one. `<id>` and its colon are
left out when the module has no `id`.

| Finding | Message |
|---|---|
| Javadoc | `TAP-X1: Javadoc is not allowed. Delete it, or write a // comment that says why.` |

## Project rules that use it

| Rule | Configuration |
|---|---|
| [`TAP-J1`](../../../docs/coding-convention/backend-java-checkstyle.md#project-rules) | no parameter besides `suggestion`: no Javadoc in backend Java; `package-info.java` is left out by a suppression filter |
