-- Test only (V2IdentityVersionMigrationTest): a role, a user and its role assignment in the form V1
-- stored them, before V2 added the VERSION columns. It runs between V1 and V2.
INSERT INTO "IDENTITY"."ROLES" ("ID", "NAME", "BUILT_IN", "DESCRIPTION", "PERMISSIONS")
VALUES ('role_seed', 'Seed role', FALSE, 'Stored before V2', '{PRESET_READ,USER_MANAGE}');

INSERT INTO "IDENTITY"."USERS" ("ID", "USERNAME", "PASSWORD_HASH", "ENABLED", "MUST_CHANGE_PASSWORD",
                                "FAILED_LOGIN_COUNT", "LOCKED_UNTIL", "CREATED_AT", "UPDATED_AT")
VALUES ('u_seed', 'seed.user', '{bcrypt}$2a$10$seed', TRUE, FALSE, 2, '2026-10-03 12:00:00+00',
        '2026-10-01 09:00:00.25+00', '2026-10-02 09:30:00+00');

INSERT INTO "IDENTITY"."USER_ROLES" ("USER_ID", "ROLE_ID")
VALUES ('u_seed', 'role_seed');
