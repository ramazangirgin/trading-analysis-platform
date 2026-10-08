-- Test only (V5IdentityPermissionArrayMigrationTest): roles in the form V3 stored them, with one
-- ROLE_PERMISSIONS row per permission key, before V5 moved the permissions into an array on the
-- role. It runs between V3 and V5.
INSERT INTO "ROLES" ("ID", "NAME", "BUILT_IN", "DESCRIPTION")
VALUES ('r_seed_all', 'seed-all', TRUE, 'Every permission'),
       ('r_seed_none', 'seed-none', FALSE, 'No permission'),
       ('r_seed_viewer', 'seed-viewer', FALSE, 'Two permissions');

INSERT INTO "ROLE_PERMISSIONS" ("ROLE_ID", "PERMISSION")
VALUES ('r_seed_all', 'analysis:run'),
       ('r_seed_all', 'analysis:read'),
       ('r_seed_all', 'analysis:read:all'),
       ('r_seed_all', 'analysis:delete'),
       ('r_seed_all', 'preset:read'),
       ('r_seed_all', 'preset:manage'),
       ('r_seed_all', 'preset:read:all'),
       ('r_seed_all', 'settings:read'),
       ('r_seed_all', 'settings:keys:write'),
       ('r_seed_all', 'user:read'),
       ('r_seed_all', 'user:manage'),
       ('r_seed_all', 'role:manage'),
       ('r_seed_all', 'audit:read'),
       ('r_seed_viewer', 'analysis:read'),
       ('r_seed_viewer', 'preset:read');

-- A user holding a role, to show that the assignments survive.
INSERT INTO "USERS" ("ID", "USERNAME", "PASSWORD_HASH", "ENABLED", "MUST_CHANGE_PASSWORD", "FAILED_LOGIN_COUNT",
                     "LOCKED_UNTIL", "CREATED_AT", "UPDATED_AT")
VALUES ('u_seed_holder', 'seed.holder', '{bcrypt}$2a$10$hash', TRUE, FALSE, 0, NULL, '2026-10-01 10:00:00+00',
        '2026-10-02 11:30:15.25+00');

INSERT INTO "USER_ROLES" ("USER_ID", "ROLE_ID")
VALUES ('u_seed_holder', 'r_seed_viewer');
