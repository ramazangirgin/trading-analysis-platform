-- Settings domain. Flyway versions are global across domains, in order of creation.
-- Names are uppercase and quoted, constraints and indexes are named explicitly.
-- Saved analysis presets. PAYLOAD is the preset's settings as an opaque JSON string, kept as TEXT so
-- it is stored exactly as written.
CREATE TABLE "PRESETS" (
    "ID"         TEXT        NOT NULL,
    "NAME"       TEXT        NOT NULL,
    "PAYLOAD"    TEXT        NOT NULL,
    "UPDATED_AT" TIMESTAMPTZ NOT NULL,
    CONSTRAINT "PRESETS_PK" PRIMARY KEY ("ID")
);
