# `RequiredAnnotationAttributeCheck`

Reports an annotation of one of the configured types that does not set every one of the required
attributes. Which annotations and which attributes are parameters; the check knows nothing about this
code base. Source:
[`RequiredAnnotationAttributeCheck.java`](../src/main/java/tr/girgin/checkstyle/RequiredAnnotationAttributeCheck.java).
Overview of all checks: [custom checks](../README.md).

## What it matches

Checkstyle sees source text only, so the check matches names as they are written:

- **An annotation** is checked wherever it stands: on a class, a method, a field, a parameter, ... It
  matches when its type is one of `annotations`, written simple or qualified (`@Mapper`,
  `@org.mapstruct.Mapper`). The type in the parameter is simple or fully qualified:
  - A simple name (`Mapper`) matches the same name in the code, written simple or qualified. It does not
    know which `Mapper` the code means.
  - A qualified name (`org.mapstruct.Mapper`) matches the same qualified name in the code, and a simple
    name that the file's imports tie to it: an `import ...Mapper;`, an `import org.mapstruct.*;` or the
    file's own package. An `import org.other.Mapper;` is not matched.
- **The attributes it sets** are those named in `name = value` pairs. A single-element annotation
  (`@Mapper("x")`, `@Mapper({"a", "b"})`) sets `value`. A marker annotation (`@Mapper`) and empty
  parentheses (`@Mapper()`) set none.
- **Where it reports:** on the annotation, once for every required attribute that is not set.

## Parameters

Set in `checkstyle.xml` as `<property name="..." value="..."/>`. `id` and `severity` are the same for
every module and not listed.

| Parameter | Type | Default | Required | Meaning |
|---|---|---|---|---|
| `annotations` | comma-separated list of types | none | yes | The annotations that must set the attributes; simple (`Mapper`) or fully qualified (`org.mapstruct.Mapper`) |
| `attributes` | comma-separated list of names | none | yes | The attributes each of those annotations must set (`config`), each a Java identifier |
| `suggestion` | string | empty | no | Appended to the message: how to fix the finding |

An invalid type in `annotations` or an invalid name in `attributes` fails the configuration with an
`IllegalArgumentException` naming it, and so does a missing `annotations` or `attributes`.

## Configuration examples

### Example 1: a mapper names its shared configuration

```xml
<module name="tr.girgin.checkstyle.RequiredAnnotationAttributeCheck">
    <property name="id" value="TAP-M1"/>
    <property name="annotations" value="org.mapstruct.Mapper"/>
    <property name="attributes" value="config"/>
    <property name="suggestion" value="Use the shared configuration: @Mapper(config = SharedMapperConfig.class)."/>
</module>
```

```java
import org.mapstruct.Mapper;

@Mapper                                            // reported
interface AToBMapper { ... }

@Mapper(uses = Converter.class)                    // reported
interface CToDMapper { ... }

@Mapper(config = SharedMapperConfig.class)         // not reported
interface EToFMapper { ... }
```

### Example 2: a scheduled job names its cron and zone

```xml
<module name="tr.girgin.checkstyle.RequiredAnnotationAttributeCheck">
    <property name="id" value="TAP-X2"/>
    <property name="annotations" value="org.springframework.scheduling.annotation.Scheduled"/>
    <property name="attributes" value="cron, zone"/>
</module>
```

```java
import org.springframework.scheduling.annotation.Scheduled;

@Scheduled(cron = "0 0 * * * *")                   // reported: 'zone' is missing
void hourly() { ... }

@Scheduled(fixedRate = 1000)                       // reported twice: 'cron' and 'zone'
void often() { ... }

@Scheduled(cron = "0 0 * * * *", zone = "UTC")     // not reported
void nightly() { ... }
```

## Message

`<id>: <what is wrong>.` followed by the `suggestion`, if there is one. `<id>` and its colon are left out
when the module has no `id`. The annotation is named as written in the code:

| Finding | Message |
|---|---|
| attribute not set | `TAP-M1: Annotation '@Mapper' does not set 'config'. Use the shared configuration: @Mapper(config = SharedMapperConfig.class).` |

## Project rules that use it

| Rule | Configuration |
|---|---|
| [`TAP-M1`](../../../docs/coding-convention/backend-java-checkstyle.md#project-rules) | `annotations = org.mapstruct.Mapper`, `attributes = config`: every mapper names the shared configuration; main and test code |
