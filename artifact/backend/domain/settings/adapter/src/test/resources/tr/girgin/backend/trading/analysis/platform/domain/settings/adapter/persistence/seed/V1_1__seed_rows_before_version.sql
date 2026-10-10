-- Test only (V2SettingsVersionMigrationTest): presets in the form V1 stored them, before V2 added the
-- VERSION column. It runs between V1 and V2.
INSERT INTO "SETTINGS"."PRESETS" ("ID", "NAME", "PAYLOAD", "UPDATED_AT")
VALUES ('p_seed_a', 'Cheap DeepSeek', '{"llmProvider":"deepseek"}', '2026-10-01 10:00:00.5+00'),
       ('p_seed_b', 'Second', '{}', '2026-10-02 11:30:15.25+00');
