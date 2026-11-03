# `ForbiddenAnnotationCheck`

Reports a use of a forbidden annotation, or, when attributes are configured, a use of it that sets a
forbidden attribute. Which annotations and which attributes are parameters; the check knows nothing about
this code base. Source:
[`ForbiddenAnnotationCheck.java`](../src/main/java/tr/girgin/checkstyle/ForbiddenAnnotationCheck.java).
Overview of all checks: [custom checks](../README.md).

## What it matches

Checkstyle sees source text only, so the check matches names as they are written:

- **An annotation** is checked wherever it stands: on a class, a method, a field, a parameter, ... A
  nested annotation (`@Mapping` inside `@Mappings({...})`) is an annotation of its own and is checked too.
  It matches when its type is one of `annotations`, written simple or qualified. The type in the
  parameter is simple or fully qualified:
  - A simple name (`Context`) matches the same name in the code, written simple or qualified. It does not
    know which `Context` the code means.
  - A qualified name (`org.mapstruct.Context`) matches the same qualified name in the code, and a simple
    name that the file's imports tie to it: an `import ...Context;`, an `import org.mapstruct.*;` or the
    file's own package. An `import org.other.Context;` is not matched.
- **Without `attributes`**, every use of a listed annotation is reported.
- **With `attributes`**, only a use that sets one of them is reported, once for every such attribute. The
  attributes it sets are those named in `name = value` pairs; a single-element annotation
  (`@Mapping("x")`) sets `value`.
- **Where it reports:** on the annotation.

## Parameters

Set in `checkstyle.xml` as `<property name="..." value="..."/>`. `id` and `severity` are the same for
every module and not listed.

| Parameter | Type | Default | Required | Meaning |
|---|---|---|---|---|
| `annotations` | comma-separated list of types | none | yes | The forbidden annotations; simple (`Context`) or fully qualified (`org.mapstruct.Context`) |
| `attributes` | comma-separated list of names | empty | no | The attributes that are forbidden on those annotations, each a Java identifier; empty means the annotations themselves are forbidden |
| `suggestion` | string | empty | no | Appended to the message: how to fix the finding |

An invalid type in `annotations` or an invalid name in `attributes` fails the configuration with an
`IllegalArgumentException` naming it, and so does a missing `annotations`.

## Configuration examples

### Example 1: an annotation that must not be used

```xml
<module name="tr.girgin.checkstyle.ForbiddenAnnotationCheck">
    <property name="id" value="TAP-M2"/>
    <property name="annotations" value="org.mapstruct.Context"/>
    <property name="suggestion" value="Put the data into the source object."/>
</module>
```

```java
import org.mapstruct.Context;

Dto map(Source source, @Context Rights rights);              // reported
Dto map(Source source, @org.mapstruct.Context Rights rights); // reported
Dto map(Source source);                                       // not reported
```

### Example 2: attributes that must not be set

```xml
<module name="tr.girgin.checkstyle.ForbiddenAnnotationCheck">
    <property name="id" value="TAP-M3"/>
    <property name="annotations" value="org.mapstruct.Mapping"/>
    <property name="attributes" value="expression, defaultExpression"/>
</module>
```

```java
import org.mapstruct.Mapping;

@Mapping(target = "a", expression = "java(x())")              // reported
@Mapping(target = "a", expression = "java(x())", defaultExpression = "java(y())") // reported twice
@Mapping(target = "a", source = "b", defaultValue = "0")     // not reported
@Mappings({@Mapping(target = "a", expression = "java(x())")}) // reported: the nested one
```

## Message

`<id>: <what is wrong>.` followed by the `suggestion`, if there is one. `<id>` and its colon are left out
when the module has no `id`. The annotation is named as written in the code:

| Finding | Message |
|---|---|
| annotation | `TAP-M2: Annotation '@Context' is not allowed. Put the data into the source object.` |
| attribute | `TAP-M3: Annotation '@Mapping' must not set 'expression'. Use source.` |

## Project rules that use it

| Rule | Configuration |
|---|---|
| [`TAP-M2`](../../../docs/coding-convention/backend-java-checkstyle.md#project-rules) | `annotations = org.mapstruct.Context`: no context parameter in a mapper; main code |
| [`TAP-M3`](../../../docs/coding-convention/backend-java-checkstyle.md#project-rules) | `annotations = org.mapstruct.Mapping`, `attributes = expression, defaultExpression, conditionExpression`: no Java in a string; main code |
