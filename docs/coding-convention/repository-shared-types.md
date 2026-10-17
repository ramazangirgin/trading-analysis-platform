# Whole repository: values shared by the backend domain, the BFF and the frontend

A value set that several layers know (the permissions of a role, a status, a rating) is written down
once, in the backend domain. Every other layer gets it in a way a tool checks, so a value that is
added, renamed or removed in the domain breaks the build where a layer is out of date, not a user at
run time. The worked example is the permissions (`Permission`).

## The rules

1. **The domain enum is the single source of the values.** `Permission` in the identity domain.
2. **The BFF API has a DTO enum mirror** (the API layer has its own types and does not use domain
   types): `PermissionDto` in `bff.controller.api.model`, serialised to the value's key with
   `@JsonValue`. A MapStruct enum mapping (`PermissionToPermissionDtoMapper`, one `map` method like
   the other mappers) converts it. MapStruct fails the compile when a domain constant has no
   counterpart in the DTO. It cannot see the DTO's own key strings, so one integration assertion
   checks them where a response carries every value (for permissions: ADMIN's `me` in
   `RolePermissionsIntegrationTest` returns exactly `Permission::key` of every constant). No extra
   mapper unit test.
3. **OpenAPI carries the values to the frontend as an enum.** `openapi.json` lists the keys as the
   enum values; `mise run api-types` regenerates `shared/api/schema.d.ts`, and `shared/api/types.ts`
   exports the type (`Permission`). The frontend never writes the set by hand. Constants kept for
   readability (`PERMISSIONS` in `features/auth/model/permissions.ts`) are checked with
   `satisfies Record<string, Permission>`, and functions that take a value (`can()`, the route gate,
   `meta.permission`) take the generated type, so an unknown or removed key fails `vue-tsc`
   (`mise run check`).
4. **A database enum, where the persistence convention asks for one, is a mirror with a test.**
   `"IDENTITY"."PERMISSION"` mirrors `Permission`, guarded by `JpaIdentityRepositoryTest`.

## Adding a value

Add the constant to the domain enum, then to the DTO enum (the compile fails until you do), then add
the database row in a migration, and run `mise run api-types` so the frontend type follows.
