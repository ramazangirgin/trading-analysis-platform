-- Settings domain, schema "SETTINGS". Flyway versions are per domain.
-- OWNER_ID is the ID of the user a preset belongs to, a plain value: no foreign key into another domain's
-- schema. It is nullable because this migration cannot look the first admin up; the application gives the
-- presets without an owner to the first admin on start.
ALTER TABLE "SETTINGS"."PRESETS" ADD COLUMN "OWNER_ID" TEXT;
CREATE INDEX "PRESETS_OWNER_ID_IDX" ON "SETTINGS"."PRESETS" ("OWNER_ID");
