# `VariableNamedAfterTypeCheck`

Reports a variable whose declared type matches a configured name pattern but whose name is not the type's
name in lower camel case. Which types are a parameter; the check knows nothing about this code base.
Source:
[`VariableNamedAfterTypeCheck.java`](../src/main/java/tr/girgin/checkstyle/VariableNamedAfterTypeCheck.java).
Overview of all checks: [custom checks](../README.md).

## What it matches

Checkstyle sees source text only, so the check reads the type as it is written:

- **A declaration** is a field, a local variable, a for-each or try-with-resources variable, a constructor,
  method or typed lambda parameter, or a record component.
- **The type** is the last segment of the written name, without type arguments: `Fakes.FakeUserRepository`
  is `FakeUserRepository`, `FooRepository<X>` is `FooRepository`. The declaration is selected when that
  name matches one of `types` (`*Service`: ends with `Service`; `Exact`; `Prefix*`; `*Middle*`).
- **The expected name** is the type's simple name with its leading capitals lowered, the last one kept
  when a lower-case letter follows: `AnalysisService` is `analysisService`, `AIService` is `aiService`.
- **With `allowQualifier`** a name that ends with the type's simple name after a lower-case qualifier is
  accepted too: `strictAnalysisService`. `StrictAnalysisService` and `myAnalysisServiceCopy` are not.
- **Not reported:** `var`, array and varargs types, primitives, `static final` fields (constants have
  their own naming rule), unnamed variables (`_`), implicitly typed lambda parameters, and a type that no
  pattern matches (`AnalysisServiceFactory` against `*Service`).
- **Where it reports:** on the variable's name.

## Parameters

Set in `checkstyle.xml` as `<property name="..." value="..."/>`. `id` and `severity` are the same for
every module and not listed.

| Parameter | Type | Default | Required | Meaning |
|---|---|---|---|---|
| `types` | comma-separated list of simple name patterns | none | yes | The types whose variables must be named after them: `Exact`, `Prefix*`, `*Suffix`, `*Middle*` |
| `allowQualifier` | boolean | `false` | no | Also accept a name that ends with the type's simple name after a lower-case qualifier |
| `suggestion` | string | empty | no | Appended to the message: how to fix the finding |

An invalid pattern in `types` fails the configuration with an `IllegalArgumentException` naming it.

## Configuration examples

### Example 1: exactly the type's name

```xml
<module name="tr.girgin.checkstyle.VariableNamedAfterTypeCheck">
    <property name="id" value="TAP-X1"/>
    <property name="types" value="*Service, *Repository"/>
    <property name="suggestion" value="Name it after its type."/>
</module>
```

```java
class OrderHandler {
    private final OrderRepository orders;                  // reported
    private final OrderRepository orderRepository;         // not reported
    private final OrderRepository strictOrderRepository;   // reported: a qualifier is not allowed

    void handle(PaymentService payments) { ... }           // reported
    void handle(PaymentService paymentService) { ... }     // not reported
    void handle(PaymentGateway gateway) { ... }            // not reported: no pattern matches
}
```

### Example 2: with a qualifier

```xml
<module name="tr.girgin.checkstyle.VariableNamedAfterTypeCheck">
    <property name="id" value="TAP-X2"/>
    <property name="types" value="*Mapper"/>
    <property name="allowQualifier" value="true"/>
</module>
```

```java
JsonMapper jsonMapper;          // not reported
JsonMapper strictJsonMapper;    // not reported: lower-case qualifier
JsonMapper mapper;              // reported
JsonMapper JsonMapper;          // reported: the qualifier must start in lower case
JsonMapper[] mappers;           // not reported: an array
static final JsonMapper SHARED; // not reported: a constant
```

## Message

`<id>: '<name>' of type '<Type>' is not named '<expected>'`, then ` (or '<qualifier><Type>')` with
`allowQualifier`, then `.` and the `suggestion`, if there is one. `<id>` and its colon are left out when
the module has no `id`:

| Finding | Message |
|---|---|
| without `allowQualifier` | `TAP-X1: 'orders' of type 'OrderRepository' is not named 'orderRepository'. Name it after its type.` |
| with `allowQualifier` | `TAP-X2: 'mapper' of type 'JsonMapper' is not named 'jsonMapper' (or '<qualifier>JsonMapper').` |

## Project rules that use it

| Rule | Configuration |
|---|---|
| [`TAP-D9`](../../../docs/coding-convention/backend-java-checkstyle.md#project-rules) | `types = *Service, *UseCase, *Port, *Mapper, *Repository, *Adapter`, `allowQualifier = true`: a collaborator is named after its type; main and test code |
