<!--
Template of a custom check's reference document. Copy it to `<CheckName>.md` in this folder, fill in
every section, delete this comment. DocumentationTest checks that the document exists, that the index
links it and that the table under "## Parameters" lists exactly the check's public setters (the first
column holds the parameter name in backticks).
-->

# `<CheckName>`

One or two sentences: what the check reports. Source:
[`<CheckName>.java`](../src/main/java/tr/girgin/checkstyle/<CheckName>.java).
Overview of all checks: [custom checks](../README.md).

## What it matches

What the check looks at, and what it does not: the syntax it reads, what counts as a match, the near
misses that are not reported (it sees source text only, there is no type resolution).

## Parameters

Set in `checkstyle.xml` as `<property name="..." value="..."/>`. `id` and `severity` are the same for
every module and not listed.

| Parameter | Type | Default | Required | Meaning |
|---|---|---|---|---|
| `name` | type | default | yes / no | what it does; the accepted forms, each with an example |

## Configuration examples

At least two different conditions. For each: the configuration, code that is reported, code that is
not.

### Example 1: what it forbids

```xml
<module name="tr.girgin.checkstyle.<CheckName>">
    <property name="id" value="TAP-X1"/>
</module>
```

```java
// reported
// not reported
```

### Example 2: another condition

## Message

The message format, with an example for a finding with the module's `id` and `suggestion`.

## Project rules that use it

| Rule | Configuration |
|---|---|
| [`TAP-X1`](../../../docs/coding-convention/backend-java-checkstyle.md#project-rules) | which parameters |
