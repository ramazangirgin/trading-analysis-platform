-- Identity domain. Flyway versions are global across domains, in order of creation.
-- Users: password_hash is the encoded hash, failed_login_count counts failed logins in a row, and
-- locked_until is set while the account is locked out.
CREATE TABLE users (
    id                   TEXT PRIMARY KEY,
    username             TEXT        NOT NULL,
    password_hash        TEXT        NOT NULL,
    enabled              BOOLEAN     NOT NULL,
    must_change_password BOOLEAN     NOT NULL,
    failed_login_count   INTEGER     NOT NULL DEFAULT 0,
    locked_until         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL
);

-- Usernames are unique ignoring case. lower() folds non-ASCII letters by the database's locale, so
-- the core only allows ASCII usernames.
CREATE UNIQUE INDEX users_username_lower ON users (lower(username));

-- Roles: built_in marks the roles the platform ships.
CREATE TABLE roles (
    id          TEXT PRIMARY KEY,
    name        TEXT    NOT NULL UNIQUE,
    built_in    BOOLEAN NOT NULL,
    description TEXT    NOT NULL DEFAULT ''
);

-- The permissions a role grants.
CREATE TABLE role_permissions (
    role_id    TEXT NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission TEXT NOT NULL,
    PRIMARY KEY (role_id, permission)
);

-- The roles a user has. No cascade on role_id: a role still assigned to users cannot be deleted.
CREATE TABLE user_roles (
    user_id TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id TEXT NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX user_roles_role_id ON user_roles (role_id);
