# `ForbiddenMemberAccessCheck`

Reports a reference to a forbidden field, a call of a forbidden method or a call of a forbidden
constructor. What is forbidden is a list of patterns, `Type#member`; the check knows nothing about this
code base. Source:
[`ForbiddenMemberAccessCheck.java`](../../../build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/ForbiddenMemberAccessCheck.java).
Overview of all checks: [custom checks](../backend-java-checkstyle-custom-checks.md).

## What it matches

Checkstyle sees source text only, so the check matches names as they are written:

- **A field** is a name after a dot: `System.out`, `java.lang.System.out`, `holder.value`. It is reported
  where it is read, also as part of a call (`System.out.println(x)` reports `System.out`) and in a
  method reference (`System.out::println`).
- **A method** is a call with a receiver: `Thread.sleep(1)`, `e.printStackTrace()`. A call without a
  receiver (`sleep(1)`, `printStackTrace()`, or a statically imported method) is not reported.
- **A constructor** is `new Type(...)`, also with a body (`new Random() { }`). Array creation
  (`new Random[3]`, `new int[3]`) is not a constructor call.
- **The type** in a pattern is a simple or a fully qualified name:
  - A simple name (`System`) matches the same name in the code, written simple or qualified
    (`System.out`, `java.lang.System.out`). It does not know which `System` the code means.
  - A qualified name (`java.lang.System`) matches the same qualified name in the code, and a simple name
    that the file's imports tie to it: an `import java.lang.System;`, an `import java.lang.*;`, no import
    for a `java.lang` type, or the file's own package. A different `import com.example.System;` is not
    matched. A partly qualified name (`lang.System`) is not matched.
- **The receiver** of a method must be a plain name (`Thread`, `TimeUnit.SECONDS`) to match a pattern
  with a type. A call, `this` or a literal as receiver (`failure().printStackTrace()`) matches only
  the type `*`.
- **Not reported:** a local variable or parameter with the field's name (`out.println()`), a statically
  imported field (`import static java.lang.System.out;` and then `out.println()`), the import itself,
  names in comments and string literals, `Foo.class` and `Foo.this`.
- Each place is reported once, for the first pattern in `members` that matches.

## Parameters

Set in `checkstyle.xml` as `<property name="..." value="..."/>`. `id` and `severity` are the same for
every module and not listed.

| Parameter | Type | Default | Required | Meaning |
|---|---|---|---|---|
| `members` | comma-separated list of patterns | none | yes | The forbidden members, one pattern each; see the forms below |
| `suggestion` | string | empty | no | Appended to the message: how to fix the finding |

A pattern in `members` is `Type#member`, one of these forms. An invalid pattern fails the
configuration with an `IllegalArgumentException` naming it.

| Form | Matches | Example |
|---|---|---|
| `Type#field` | the field of the type | `System#out` |
| `Type#method()` | a call with any arguments | `Thread#sleep()` |
| `Type#method(n)` | a call with exactly `n` arguments | `Instant#now(0)`: `Instant.now()`, not `Instant.now(clock)` |
| `Type#new()` | a constructor call with any arguments | `Random#new()` |
| `Type#new(n)` | a constructor call with exactly `n` arguments | `Random#new(0)` |
| `*#member` | any receiver, also one that is not a plain name | `*#printStackTrace(0)` |
| `*#new(n)` | any type | `*#new(0)` |
| `Type.*#member` | a constant or field of the type as the receiver | `TimeUnit.*#sleep()`: `TimeUnit.SECONDS.sleep(1)` |
| `Type#name*` | a name that starts with `name` (`*` at the end) | `Executors#new*()` |

`Type` is simple or fully qualified (`Instant`, `java.time.Instant`), see above.
`Type.*#new()` is not allowed: a constructor has no receiver.

## Configuration examples

### Example 1: no `System.out` and `System.err`

```xml
<module name="tr.girgin.checkstyle.ForbiddenMemberAccessCheck">
    <property name="id" value="TAP-X1"/>
    <property name="members" value="System#out, System#err"/>
    <property name="suggestion" value="Use an SLF4J logger: log.info(...)."/>
</module>
```

```java
System.out.println("started");          // reported
java.lang.System.err.println("failed"); // reported
Runnable r = System.out::println;       // reported

out.println("x");                       // not reported: a local variable or a static import
System.getProperty("user.dir");         // not reported: another member of System
String text = "System.out";             // not reported: a string
```

### Example 2: no `Instant.now()` without a clock

```xml
<module name="tr.girgin.checkstyle.ForbiddenMemberAccessCheck">
    <property name="id" value="TAP-X2"/>
    <property name="members" value="java.time.Instant#now(0), java.time.LocalDate#now(0)"/>
    <property name="suggestion" value="Take the time from an injected java.time.Clock."/>
</module>
```

```java
import java.time.Instant;

Instant.now();                          // reported
java.time.Instant.now();                // reported
Instant.now(clock);                     // not reported: one argument
Duration.now();                         // not reported: another type
```

With `import com.example.Instant;` instead, `Instant.now()` is not reported, because the qualified name
in the pattern does not match the import. With the simple name `Instant#now(0)` it would be reported.

### Example 3: no constructor call, no prefix of method names, any receiver

```xml
<module name="tr.girgin.checkstyle.ForbiddenMemberAccessCheck">
    <property name="id" value="TAP-X3"/>
    <property name="members" value="Random#new(), Executors#new*(), *#printStackTrace(0), TimeUnit.*#sleep()"/>
</module>
```

```java
new Random();                           // reported (any arguments: new Random(7) too)
Executors.newFixedThreadPool(2);        // reported
failure().printStackTrace();            // reported: any receiver
TimeUnit.SECONDS.sleep(1);              // reported

new Random[3];                          // not reported: an array
Executors.callable(task);               // not reported: the name does not start with "new"
failure.printStackTrace(System.out);    // not reported: one argument
```

## Message

`<id>: <what is wrong>.` followed by the `suggestion`, if there is one. `<id>` and its colon are left out
when the module has no `id`. The pattern is shown as it is written in `members`:

| Finding | Message |
|---|---|
| field | `TAP-X1: Field 'System#out' is not allowed. Use an SLF4J logger: log.info(...).` |
| method | `TAP-X2: Call of 'Instant#now(0)' is not allowed. Take the time from an injected java.time.Clock.` |
| constructor | `TAP-X3: Call of constructor 'Random#new()' is not allowed.` |

## Project rules that use it

| Rule | Configuration |
|---|---|
| [`TAP-L1`](../backend-java-checkstyle.md#project-rules) | `members = System#out, System#err, *#printStackTrace(0)`: no console output, use SLF4J |
