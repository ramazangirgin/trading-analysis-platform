-- Identity domain: everything here lives in the "IDENTITY" schema, which Flyway creates. Flyway versions
-- are per domain: identity starts at V1 and has its own history table "IDENTITY"."FLYWAY_SCHEMA_HISTORY".
-- Names are uppercase and quoted, constraints and indexes are named explicitly, and every name that can
-- carry a schema does (an index and a constraint live in their table's schema).
-- The permissions of a role are an array of a PostgreSQL enum type on the role's row. The labels are the
-- Java constant names of the Permission enum (ANALYSIS_READ_ALL), not the keys (analysis:read:all) that
-- the API and the wire use. USER_ROLES is a join table: an array element cannot have a foreign key, and
-- USER_ROLES_ROLE_ID_FK keeps a role that is still assigned from being deleted.
CREATE TYPE "IDENTITY"."PERMISSION" AS ENUM (
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

COMMENT ON TYPE "IDENTITY"."PERMISSION" IS 'Mirrors the Java enum Permission';

-- Users: PASSWORD_HASH is the encoded hash, FAILED_LOGIN_COUNT counts failed logins in a row, and
-- LOCKED_UNTIL is set while the account is locked out.
CREATE TABLE "IDENTITY"."USERS" (
    "ID"                   TEXT        NOT NULL,
    "USERNAME"             TEXT        NOT NULL,
    "PASSWORD_HASH"        TEXT        NOT NULL,
    "ENABLED"              BOOLEAN     NOT NULL,
    "MUST_CHANGE_PASSWORD" BOOLEAN     NOT NULL,
    "FAILED_LOGIN_COUNT"   INTEGER     NOT NULL DEFAULT 0,
    "LOCKED_UNTIL"         TIMESTAMPTZ,
    "CREATED_AT"           TIMESTAMPTZ NOT NULL,
    "UPDATED_AT"           TIMESTAMPTZ NOT NULL,
    CONSTRAINT "USERS_PK" PRIMARY KEY ("ID")
);

-- Usernames are unique ignoring case. LOWER folds non-ASCII letters by the database's locale, so
-- the core only allows ASCII usernames.
CREATE UNIQUE INDEX "USERS_USERNAME_LOWER_UK" ON "IDENTITY"."USERS" (LOWER("USERNAME"));

-- Roles: BUILT_IN marks the roles the platform ships; PERMISSIONS are the permissions a role grants.
CREATE TABLE "IDENTITY"."ROLES" (
    "ID"          TEXT                        NOT NULL,
    "NAME"        TEXT                        NOT NULL,
    "BUILT_IN"    BOOLEAN                     NOT NULL,
    "DESCRIPTION" TEXT                        NOT NULL DEFAULT '',
    "PERMISSIONS" "IDENTITY"."PERMISSION"[]   NOT NULL DEFAULT '{}',
    CONSTRAINT "ROLES_PK" PRIMARY KEY ("ID"),
    CONSTRAINT "ROLES_NAME_UK" UNIQUE ("NAME")
);

-- The roles a user has. No cascade on ROLE_ID: a role still assigned to users cannot be deleted.
CREATE TABLE "IDENTITY"."USER_ROLES" (
    "USER_ID" TEXT NOT NULL,
    "ROLE_ID" TEXT NOT NULL,
    CONSTRAINT "USER_ROLES_PK" PRIMARY KEY ("USER_ID", "ROLE_ID"),
    CONSTRAINT "USER_ROLES_USER_ID_FK" FOREIGN KEY ("USER_ID") REFERENCES "IDENTITY"."USERS" ("ID") ON DELETE CASCADE,
    CONSTRAINT "USER_ROLES_ROLE_ID_FK" FOREIGN KEY ("ROLE_ID") REFERENCES "IDENTITY"."ROLES" ("ID")
);

CREATE INDEX "USER_ROLES_ROLE_ID_IDX" ON "IDENTITY"."USER_ROLES" ("ROLE_ID");
