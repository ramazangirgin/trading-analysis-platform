-- Identity domain. Flyway versions are global across domains, in order of creation.
-- Names are uppercase and quoted, constraints and indexes are named explicitly.
-- Users: PASSWORD_HASH is the encoded hash, FAILED_LOGIN_COUNT counts failed logins in a row, and
-- LOCKED_UNTIL is set while the account is locked out.
CREATE TABLE "USERS" (
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
CREATE UNIQUE INDEX "USERS_USERNAME_LOWER_UK" ON "USERS" (LOWER("USERNAME"));

-- Roles: BUILT_IN marks the roles the platform ships.
CREATE TABLE "ROLES" (
    "ID"          TEXT    NOT NULL,
    "NAME"        TEXT    NOT NULL,
    "BUILT_IN"    BOOLEAN NOT NULL,
    "DESCRIPTION" TEXT    NOT NULL DEFAULT '',
    CONSTRAINT "ROLES_PK" PRIMARY KEY ("ID"),
    CONSTRAINT "ROLES_NAME_UK" UNIQUE ("NAME")
);

-- The permissions a role grants.
CREATE TABLE "ROLE_PERMISSIONS" (
    "ROLE_ID"    TEXT NOT NULL,
    "PERMISSION" TEXT NOT NULL,
    CONSTRAINT "ROLE_PERMISSIONS_PK" PRIMARY KEY ("ROLE_ID", "PERMISSION"),
    CONSTRAINT "ROLE_PERMISSIONS_ROLE_ID_FK" FOREIGN KEY ("ROLE_ID") REFERENCES "ROLES" ("ID") ON DELETE CASCADE
);

-- The roles a user has. No cascade on ROLE_ID: a role still assigned to users cannot be deleted.
CREATE TABLE "USER_ROLES" (
    "USER_ID" TEXT NOT NULL,
    "ROLE_ID" TEXT NOT NULL,
    CONSTRAINT "USER_ROLES_PK" PRIMARY KEY ("USER_ID", "ROLE_ID"),
    CONSTRAINT "USER_ROLES_USER_ID_FK" FOREIGN KEY ("USER_ID") REFERENCES "USERS" ("ID") ON DELETE CASCADE,
    CONSTRAINT "USER_ROLES_ROLE_ID_FK" FOREIGN KEY ("ROLE_ID") REFERENCES "ROLES" ("ID")
);

CREATE INDEX "USER_ROLES_ROLE_ID_IDX" ON "USER_ROLES" ("ROLE_ID");
