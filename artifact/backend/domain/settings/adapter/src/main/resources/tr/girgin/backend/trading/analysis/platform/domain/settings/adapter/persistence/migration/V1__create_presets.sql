-- Settings domain: everything here lives in the "SETTINGS" schema, which Flyway creates. Flyway versions
-- are per domain: settings starts at V1 and has its own history table "SETTINGS"."FLYWAY_SCHEMA_HISTORY".
-- Names are uppercase and quoted, constraints and indexes are named explicitly, and every name that can
-- carry a schema does.
-- Saved analysis presets. PAYLOAD is the preset's settings as an opaque JSON string, kept as TEXT so
-- it is stored exactly as written.
CREATE TABLE "SETTINGS"."PRESETS" (
    "ID"         TEXT        NOT NULL,
    "NAME"       TEXT        NOT NULL,
    "PAYLOAD"    TEXT        NOT NULL,
    "UPDATED_AT" TIMESTAMPTZ NOT NULL,
    CONSTRAINT "PRESETS_PK" PRIMARY KEY ("ID")
);
