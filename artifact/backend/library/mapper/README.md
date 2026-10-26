# Shared library: mapper

`:backend:library:mapper` holds the MapStruct configuration every mapper uses, with its mapping control, and the generic
scalar mappers that more than one module needs. Like
every shared library it holds no domain type and no business rule, and names no domain, not even in
its tests ([package structure](../../../../docs/coding-convention/backend-java-package-structure.md#shared-libraries)).

## What it holds

| Class | Maps | Used by |
|---|---|---|
| `DurationToMillisMapper` | `Duration` to its milliseconds (`long`) | the BFF's and the domain adapters' MapStruct mappers |
| `SharedMapperConfig` | The `@MapperConfig` of every mapper: Spring component, constructor injection, deep copy, unmapped target and source properties fail the compile | every mapper of the backend |
| `DeepCloneWithConversions` | The mapping control of `SharedMapperConfig`: mapping methods and built-in conversions only | `SharedMapperConfig` |
| `EnumToLowerCaseNameMapper` | An enum to its lower-case constant name (`SAMPLE_VALUE` to `sample_value`), only where a mapping asks for it with `qualifiedByName = "lowerCaseName"` | the BFF's and the domain adapters' MapStruct mappers |

Only MapStruct mappers, the shared config and its mapping control live here
(`mapper_packages_hold_only_mappers`). The generated mappers are Spring beans.

## Decisions

- **One configuration for every mapper.** Every `@Mapper` sets `config = SharedMapperConfig.class`,
  enforced by `TAP-M1`. The config makes MapStruct deep-copy what it maps, so a mapped object never
  shares a mutable object with its source, and sets both unmapped policies to `ERROR`: a target
  property without a source and a source property that is not mapped fail the compile. A source
  property left out on purpose is listed in `@BeanMapping(ignoreUnmappedSourceProperties = ...)`
  on the mapper's `map` method. The mapping control is `DeepCloneWithConversions`, not MapStruct's
  `DeepClone`: `DeepClone` also forbids built-in conversions (enum to `String`, `Double` to
  `BigDecimal`, `LocalDate` to `String`), which always create a new immutable value and cannot
  share state. The config carries no `uses`: every mapper names its own converters, so it stays
  visible which ones it uses.

- **A converter moves here with its second user.** A generic converter that one module needs stays in
  that module's `mapper` package; once a second module needs it, it moves here instead of being
  copied.
- **The mapper is generic, the mapping that applies it is not.** `EnumToLowerCaseNameMapper` knows
  nothing about the enums it is used for. Which enum is mapped to its lower-case name, and where, is
  decided by the mappings in the modules that use it, and stays there.
