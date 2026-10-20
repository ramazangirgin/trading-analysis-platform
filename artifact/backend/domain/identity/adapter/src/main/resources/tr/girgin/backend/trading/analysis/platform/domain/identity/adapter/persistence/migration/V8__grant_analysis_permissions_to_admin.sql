-- ADMIN's array was materialised from the enum when V3 ran, so new values are not picked up.
UPDATE "IDENTITY"."ROLES"
SET "PERMISSIONS" = "PERMISSIONS" || ARRAY['ANALYSIS_DEFAULTS_MANAGE', 'ANALYSIS_OVERRIDE_MODELS']::"IDENTITY"."PERMISSION"[]
WHERE "ID" = 'ADMIN';
