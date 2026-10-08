-- Identity domain. Flyway versions are global across domains: identity owns V3 and V5.
-- Names are uppercase and quoted, constraints and indexes are named explicitly.
-- The permissions of a role become an array of a PostgreSQL enum type on the role's row, in place of
-- the ROLE_PERMISSIONS table. The labels are the Java constant names of the Permission enum
-- (ANALYSIS_READ_ALL), not the keys (analysis:read:all) that the API and the wire use.
-- USER_ROLES stays a join table: an array element cannot have a foreign key, and USER_ROLES_ROLE_ID_FK
-- keeps a role that is still assigned from being deleted.
CREATE TYPE "PERMISSION" AS ENUM (
    'ANALYSIS_RUN',
    'ANALYSIS_READ',
    'ANALYSIS_READ_ALL',
    'ANALYSIS_DELETE',
    'PRESET_READ',
    'PRESET_MANAGE',
    'PRESET_READ_ALL',
    'SETTINGS_READ',
    'SETTINGS_KEYS_WRITE',
    'USER_READ',
    'USER_MANAGE',
    'ROLE_MANAGE',
    'AUDIT_READ'
);

COMMENT ON TYPE "PERMISSION" IS 'Mirrors the Java enum Permission';

ALTER TABLE "ROLES" ADD COLUMN "PERMISSIONS" "PERMISSION"[] NOT NULL DEFAULT '{}';

-- Each key becomes its label: analysis:read:all -> ANALYSIS_READ_ALL. A key without a label fails
-- the migration instead of being dropped.
UPDATE "ROLES" SET "PERMISSIONS" = "GRANTS"."PERMISSIONS"
FROM (
    SELECT "ROLE_ID",
           ARRAY_AGG(UPPER(REPLACE("PERMISSION", ':', '_'))::"PERMISSION" ORDER BY "PERMISSION") AS "PERMISSIONS"
    FROM "ROLE_PERMISSIONS"
    GROUP BY "ROLE_ID"
) AS "GRANTS"
WHERE "ROLES"."ID" = "GRANTS"."ROLE_ID";

DROP TABLE "ROLE_PERMISSIONS";
