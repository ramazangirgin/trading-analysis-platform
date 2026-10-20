# `StaticMethodInAnnotatedClassCheck`

Reports a static method declared in a class that carries one of the configured annotations. Which
annotations are a parameter; the check knows nothing about this code base. Source:
[`StaticMethodInAnnotatedClassCheck.java`](../src/main/java/tr/girgin/checkstyle/StaticMethodInAnnotatedClassCheck.java).
Overview of all checks: [custom checks](../README.md).

## What it matches

Checkstyle sees source text only, so the check matches names as they are written:

- **A method** is reported when it is `static` (of any visibility) and declared directly in a `class`
  that carries at least one of the `annotations` on the class itself.
- **Not reported:** static fields, static initialisers, constructors, instance methods, methods of
  interfaces, records and enums, methods of an anonymous class, and methods of a nested or local class
  inside an annotated class (they are checked only when the nested class carries an annotation itself).
- **An annotation** matches when it is one of `annotations`, written simple or qualified on the class
  (`@Service`, `@org.springframework.stereotype.Service`). The type in the parameter is simple or fully
  qualified:
  - A simple name (`Service`) matches the same name in the code, written simple or qualified. It does not
    know which `Service` the code means.
  - A qualified name (`org.springframework.stereotype.Service`) matches the same qualified name in the
    code, and a simple name that the file's imports tie to it: an `import ...Service;`, an
    `import org.springframework.stereotype.*;` or the file's own package. An `import org.other.Service;`
    is not matched.
- **Where it reports:** on the method's name.

## Parameters

Set in `checkstyle.xml` as `<property name="..." value="..."/>`. `id` and `severity` are the same for
every module and not listed.

| Parameter | Type | Default | Required | Meaning |
|---|---|---|---|---|
| `annotations` | comma-separated list of types | none | yes | The annotations of the classes that must not have static methods; simple (`Service`) or fully qualified (`org.springframework.stereotype.Service`) |
| `suggestion` | string | empty | no | Appended to the message: how to fix the finding |

An invalid type in `annotations` fails the configuration with an `IllegalArgumentException` naming it.

## Configuration examples

### Example 1: no static method in a Spring bean

```xml
<module name="tr.girgin.checkstyle.StaticMethodInAnnotatedClassCheck">
    <property name="id" value="TAP-X1"/>
    <property name="annotations" value="org.springframework.stereotype.Service, org.springframework.stereotype.Component"/>
    <property name="suggestion" value="Make it an instance method."/>
</module>
```

```java
import org.springframework.stereotype.Service;

@Service
class OrderService {

    static final int LIMIT = 3;                  // not reported: a field

    static String format(String id) { ... }      // reported

    String describe(String id) { ... }           // not reported: an instance method

    static class Parser {
        static int parse(String text) { ... }    // not reported: the nested class has no annotation
    }
}

class OrderFormat {
    static String format(String id) { ... }      // not reported: no annotation
}
```

### Example 2: no static method in an entity

```xml
<module name="tr.girgin.checkstyle.StaticMethodInAnnotatedClassCheck">
    <property name="id" value="TAP-X2"/>
    <property name="annotations" value="jakarta.persistence.Entity"/>
</module>
```

```java
import jakarta.persistence.Entity;

@Entity
class OrderEntity {
    static OrderEntity of(String id) { ... }     // reported
}
```

## Message

`<id>: <what is wrong>.` followed by the `suggestion`, if there is one. `<id>` and its colon are left out
when the module has no `id`. The class's matching annotations are listed by the names written on the
class:

| Finding | Message |
|---|---|
| static method | `TAP-X1: Static method 'format' in 'OrderService', which is annotated @Service. Make it an instance method.` |

## Project rules that use it

| Rule | Configuration |
|---|---|
| [`TAP-D7`](../../../docs/coding-convention/backend-java-checkstyle.md#project-rules) | `annotations = @Service, @Component, @Repository, @Controller, @RestController, @RestControllerAdvice, @Configuration`: no static method in a Spring bean; main code only |
