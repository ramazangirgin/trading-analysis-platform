# `RequiredMethodAnnotationCheck`

Reports a public method of a selected class that carries none of the required annotations. Which classes
and which annotations are parameters; the check knows nothing about this code base. Source:
[`RequiredMethodAnnotationCheck.java`](../src/main/java/tr/girgin/checkstyle/RequiredMethodAnnotationCheck.java).
Overview of all checks: [custom checks](../README.md).

## What it matches

Checkstyle sees source text only, so the check matches names as they are written:

- **A method** is checked when it is `public` and declared directly in a `class` whose simple name
  matches one of the `classes` patterns. Static public methods are checked too.
- **Not checked:** constructors, non-public methods (package-private, `protected`, `private`), methods of
  interfaces, records and enums, methods of an anonymous class, and methods of a nested or local class
  inside a matching class (they are checked only when the nested class's own name matches).
- **A class pattern** is matched against the class's simple name, never the package. It is a Java
  identifier with an optional `*` at its start and/or its end.
- **An annotation** satisfies the rule when it is one of `annotations`, written simple or qualified on the
  method (`@Required`, `@com.example.Required`). The type in the parameter is simple or fully qualified:
  - A simple name (`Required`) matches the same name in the code, written simple or qualified. It does not
    know which `Required` the code means.
  - A qualified name (`com.example.Required`) matches the same qualified name in the code, and a simple
    name that the file's imports tie to it: an `import com.example.Required;`, an `import com.example.*;`
    or the file's own package. An `import org.other.Required;` is not matched.
- **Where it reports:** on the method's name, so the line is the signature, not the first annotation.
- Annotations on the class, on parameters or on the return type do not count; the annotation's
  arguments are not looked at.

## Parameters

Set in `checkstyle.xml` as `<property name="..." value="..."/>`. `id` and `severity` are the same for
every module and not listed.

| Parameter | Type | Default | Required | Meaning |
|---|---|---|---|---|
| `classes` | comma-separated list of class-name patterns | none | yes | The classes whose public methods are checked; see the forms below |
| `annotations` | comma-separated list of types | none | yes | The annotations, any one of which satisfies the rule; simple (`Required`) or fully qualified (`com.example.Required`) |
| `suggestion` | string | empty | no | Appended to the message: how to fix the finding |

A pattern in `classes` is one of these forms. Anything else (a dot, a `*` in the middle, an empty
value) fails the configuration with an `IllegalArgumentException` naming it. An invalid type in
`annotations` fails it the same way.

| Form | Matches | Example |
|---|---|---|
| a plain name | exactly that class name | `Exact` |
| `Name*` | a name that starts with `Name` | `Order*`: `OrderService` |
| `*Name` | a name that ends with `Name` | `*ApiDelegateImpl`: `AuthApiDelegateImpl` |
| `*Name*` | a name that contains `Name` | `*Delegate*` |
| `*` | every class | `*` |

## Configuration examples

### Example 1: every public method of a delegate says who may call it

```xml
<module name="tr.girgin.checkstyle.RequiredMethodAnnotationCheck">
    <property name="id" value="TAP-X1"/>
    <property name="classes" value="*ApiDelegateImpl"/>
    <property name="annotations" value="com.example.RequiresPermission, com.example.NoPermissionRequired"/>
    <property name="suggestion" value="Declare who may call it."/>
</module>
```

```java
import com.example.RequiresPermission;

class UsersApiDelegateImpl {

    @RequiresPermission(Permission.USERS_READ)
    public ResponseEntity<User> getUser(long id) { ... }      // not reported

    @com.example.NoPermissionRequired("open route")
    public ResponseEntity<Void> ping() { ... }                // not reported

    public ResponseEntity<Void> deleteUser(long id) { ... }   // reported

    @Override
    public String toString() { ... }                          // reported: @Override is not required

    ResponseEntity<Void> helper() { ... }                     // not reported: not public
}

class UsersService {
    public void rename() { ... }                              // not reported: the class does not match
}
```

With `import org.other.RequiresPermission;` instead, `@RequiresPermission` does not count, because the
qualified name in the parameter does not match the import.

### Example 2: every public method of a service or a handler is transactional or read-only

```xml
<module name="tr.girgin.checkstyle.RequiredMethodAnnotationCheck">
    <property name="id" value="TAP-X2"/>
    <property name="classes" value="*Service, *Handler"/>
    <property name="annotations" value="Transactional, ReadOnly"/>
</module>
```

```java
class OrderService {

    @Transactional
    public void place() { ... }        // not reported

    @ReadOnly
    public Order find() { ... }        // not reported

    public void cancel() { ... }       // reported
}

class OrderController {
    public void cancel() { ... }       // not reported: neither *Service nor *Handler
}
```

## Message

`<id>: <what is wrong>.` followed by the `suggestion`, if there is one. `<id>` and its colon are left out
when the module has no `id`. The annotations are listed by their simple names, as in the parameter:

| Finding | Message |
|---|---|
| method | `TAP-X1: Method 'deleteUser' of 'UsersApiDelegateImpl' has none of the annotations @RequiresPermission, @NoPermissionRequired. Declare who may call it.` |

## Project rules that use it

| Rule | Configuration |
|---|---|
| [`TAP-A1`](../../../docs/coding-convention/backend-java-checkstyle.md#project-rules) | `classes = *ApiDelegateImpl`, `annotations = RequiresPermission, NoPermissionRequired`: every public method of a BFF delegate declares who may call it (added with the project rule) |
