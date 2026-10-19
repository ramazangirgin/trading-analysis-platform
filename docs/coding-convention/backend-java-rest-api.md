# Backend: REST endpoints

How a BFF endpoint answers: what a controller and its delegate return, which media types they
declare, and where an error becomes an HTTP status. The packages these classes live in are in
[package structure](backend-java-package-structure.md).

## Return types

| The endpoint answers | Controller and delegate return | Example |
|---|---|---|
| Only `200` with a body | the body type itself, never `ResponseEntity` | `UserDto getUser(String id)`, `List<RoleDto> listRoles()` |
| `201` (with `Location`), `204`, or a status or header that varies | `ResponseEntity<T>` | `ResponseEntity.created(uri).body(dto)`, `ResponseEntity.noContent().build()` |

Every error is an exception, never a `ResponseEntity` built by the delegate (see [Errors](#errors)).
So a method whose only success is `200` has nothing to set on a `ResponseEntity`, and Spring answers
`200` for a returned body.

Checked by `TAP-D6` ([Checkstyle](backend-java-checkstyle.md#rules-catalogue)): `ResponseEntity.ok(...)`
and `ResponseEntity::ok` fail the build. A method whose success status really varies between `200`
and another one suppresses it, with the reason: `@SuppressWarnings("checkstyle:TAP-D6")`.

## Media types

JSON is the default for request and response bodies: a mapping does not declare `produces` or
`consumes = application/json`. `springdoc.default-produces-media-type=application/json` keeps it in
the OpenAPI document. Another media type is declared: `produces = MediaType.TEXT_EVENT_STREAM_VALUE`
on the event stream.

Checked by `TAP-D5` ([Checkstyle](backend-java-checkstyle.md#rules-catalogue)).

## Errors

A delegate does not catch an exception to turn it into an HTTP answer. It lets it through, and an
exception handler (`@RestControllerAdvice`) answers with an `ApiErrorDto`:

| Exception | Handler | Mapping |
|---|---|---|
| `ApiException` (thrown by a delegate for a web-only error, e.g. an empty PATCH), invalid or unreadable bodies, bad parameters | `ApiExceptionHandler` in `bff.controller.api.error` | in the handler |
| A domain's exception (`AnalysisException`, `IdentityException`, `SettingsException`, `CatalogUnavailableException`) | `DomainExceptionHandler` in `bff.delegate.impl.error` | the domain's `*ExceptionToApiExceptionMapper` in `bff.delegate.impl.mapper.error` |

`DomainExceptionHandler` lives in `:backend:bff:impl` because `:backend:bff:api` does not depend on
the domains. A new domain exception gets a mapper and an `@ExceptionHandler` method there.

A delegate may still catch a domain exception when it has to act on it before the answer (e.g.
`changePassword` ends the session on `BAD_CREDENTIALS`); it rethrows it, or throws an
`ApiException`, and does not call a mapper.

Checked by ArchUnit (`ArchitectureTest`): `exception_mappers_are_used_only_by_exception_handlers`
(only `bff.delegate.impl.error` uses the mappers), `exception_handlers_live_in_error_packages`,
`bff_impl_error_holds_only_exception_handlers`.
