-- Settings domain. Flyway versions are global across domains, in order of creation.
-- Saved analysis presets. payload is the preset's settings as an opaque JSON string, kept as TEXT so
-- it is stored exactly as written.
CREATE TABLE presets (
    id         TEXT PRIMARY KEY,
    name       TEXT        NOT NULL,
    payload    TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
