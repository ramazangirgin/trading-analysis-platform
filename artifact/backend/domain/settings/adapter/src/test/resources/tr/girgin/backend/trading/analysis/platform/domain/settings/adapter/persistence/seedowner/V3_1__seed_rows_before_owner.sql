-- Test only (V4SettingsOwnerMigrationTest): a preset in the form V3 stored it, before V4 added the
-- OWNER_ID column. It runs between V3 and V4.
INSERT INTO "SETTINGS"."PRESETS" ("ID", "NAME", "PAYLOAD", "UPDATED_AT", "VERSION")
VALUES ('p_seed_owner', 'Before owners', '{"llmProvider":"deepseek"}', '2026-10-03 09:15:00+00', 3);
