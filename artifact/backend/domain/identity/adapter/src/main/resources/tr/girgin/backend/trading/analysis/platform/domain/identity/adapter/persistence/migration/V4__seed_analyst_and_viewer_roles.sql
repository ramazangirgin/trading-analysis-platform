-- The built-in ANALYST and VIEWER roles (#87), with fixed IDs equal to their names so the core can
-- name them without a lookup by name (RoleId.ANALYST, RoleId.VIEWER), like ADMIN in V3. "Their own"
-- in the descriptions becomes true with the ownership issues (#91, #92); until then the read
-- permissions cover every analysis and preset. A new Permission constant needs a migration that
-- decides which built-in roles get it (ADMIN always does).
INSERT INTO "IDENTITY"."ROLES" ("ID", "NAME", "BUILT_IN", "DESCRIPTION", "PERMISSIONS")
VALUES (
    'ANALYST',
    'ANALYST',
    TRUE,
    'Runs analyses, reads and deletes their own, manages their own presets, reads the settings',
    ARRAY[
        'ANALYSIS_RUN',
        'ANALYSIS_READ',
        'ANALYSIS_DELETE',
        'PRESET_READ',
        'PRESET_MANAGE',
        'SETTINGS_READ'
    ]::"IDENTITY"."PERMISSION"[]
),
(
    'VIEWER',
    'VIEWER',
    TRUE,
    'Reads their own analyses and presets',
    ARRAY['ANALYSIS_READ', 'PRESET_READ']::"IDENTITY"."PERMISSION"[]
);
