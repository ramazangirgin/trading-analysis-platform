-- Identity domain. Flyway versions are global across domains, in order of creation.
-- One migration for SQLite and PostgreSQL: every column is TEXT, BOOLEAN or a small INTEGER
-- (failed_login_count fits 4 bytes), which mean the same in both, so there is no
-- db/migration-postgresql counterpart (unlike V4_1 of the analysis domain). Times are UTC
-- fixed-width ISO-8601 text, as in analyses and presets.
CREATE TABLE users (
    id                   TEXT PRIMARY KEY,
    username             TEXT    NOT NULL,
    password_hash        TEXT    NOT NULL,
    enabled              BOOLEAN NOT NULL,
    must_change_password BOOLEAN NOT NULL,
    failed_login_count   INTEGER NOT NULL DEFAULT 0,
    locked_until         TEXT,
    created_at           TEXT    NOT NULL,
    updated_at           TEXT    NOT NULL
);

-- Usernames are unique ignoring case; both databases fold ASCII with lower(), and the core only
-- allows ASCII usernames.
CREATE UNIQUE INDEX users_username_lower ON users (lower(username));

CREATE TABLE roles (
    id          TEXT PRIMARY KEY,
    name        TEXT    NOT NULL UNIQUE,
    built_in    BOOLEAN NOT NULL,
    description TEXT    NOT NULL DEFAULT ''
);

CREATE TABLE role_permissions (
    role_id    TEXT NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission TEXT NOT NULL,
    PRIMARY KEY (role_id, permission)
);

-- No cascade on role_id: a role still assigned to users cannot be deleted.
CREATE TABLE user_roles (
    user_id TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id TEXT NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX user_roles_role_id ON user_roles (role_id);
