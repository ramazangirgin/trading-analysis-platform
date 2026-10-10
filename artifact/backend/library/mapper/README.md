# Shared library: mapper

`:backend:library:mapper` holds generic MapStruct scalar mappers that more than one module needs. Like
every shared library it holds no domain type and no business rule, and names no domain, not even in
its tests ([package structure](../../../../docs/coding-convention/backend-java-package-structure.md#shared-libraries)).

## What it holds

| Class | Maps | Used by |
|---|---|---|
| `DurationToMillisMapper` | `Duration` to its milliseconds (`long`) | `:backend:bff:impl`, `:backend:domain:analysis:adapter` |
| `EnumToLowerCaseNameMapper` | An enum to its lower-case constant name (`SAMPLE_VALUE` to `sample_value`), only where a mapping asks for it with `qualifiedByName = "lowerCaseName"` | `:backend:bff:impl`, `:backend:domain:analysis:adapter` |

Only MapStruct mappers live here (`mapper_packages_hold_only_mappers`). The generated mappers are
Spring beans.

## Decisions

- **A converter moves here with its second user.** A generic converter that one module needs stays in
  that module's `mapper` package; once a second module needs it, it moves here instead of being
  copied.
- **The mapper is generic, the mapping that applies it is not.** `EnumToLowerCaseNameMapper` knows
  nothing about the enums it is used for. Which enum is mapped to its lower-case name, and where, is
  decided by the mappings in the modules that use it, and stays there.
