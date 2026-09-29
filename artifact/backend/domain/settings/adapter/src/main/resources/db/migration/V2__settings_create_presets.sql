-- Settings domain. Flyway versions are global across domains, in order of creation.
CREATE TABLE presets (
    id         TEXT PRIMARY KEY,
    name       TEXT NOT NULL,
    payload    TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
