-- Nullable: no migration can look the first admin up; the application gives presets without an owner to the first admin on start.
ALTER TABLE "SETTINGS"."PRESETS" ADD COLUMN "OWNER_ID" TEXT;
CREATE INDEX "PRESETS_OWNER_ID_IDX" ON "SETTINGS"."PRESETS" ("OWNER_ID");
