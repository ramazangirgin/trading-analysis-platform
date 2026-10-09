-- Test only (V7SettingsVersionMigrationTest): presets in the form V2 stored them, before V7 added
-- the VERSION column. It runs between V2 and V7.
INSERT INTO "PRESETS" ("ID", "NAME", "PAYLOAD", "UPDATED_AT")
VALUES ('p_seed_a', 'Cheap DeepSeek', '{"llmProvider":"deepseek"}', '2026-10-01 10:00:00.5+00'),
       ('p_seed_b', 'Second', '{}', '2026-10-02 11:30:15.25+00');
