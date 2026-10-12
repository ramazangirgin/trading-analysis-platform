-- The built-in ADMIN role (#82), with a fixed ID so the core can name it without a lookup by name
-- (RoleId.ADMIN). It holds every permission that exists now. A new Permission constant needs a
-- migration that adds it to this role (#87 keeps the built-in roles complete and seeds the others).
INSERT INTO "IDENTITY"."ROLES" ("ID", "NAME", "BUILT_IN", "DESCRIPTION", "PERMISSIONS")
VALUES (
    'ADMIN',
    'ADMIN',
    TRUE,
    'Everything, including users, roles and API keys',
    ARRAY(SELECT UNNEST(ENUM_RANGE(NULL::"IDENTITY"."PERMISSION")))::"IDENTITY"."PERMISSION"[]
);
