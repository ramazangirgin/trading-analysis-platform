-- The labels are the Java constant names, not the keys. A transaction cannot use an enum value it
-- added, so the grant to ADMIN is the next migration.
ALTER TYPE "IDENTITY"."PERMISSION" ADD VALUE 'ANALYSIS_DEFAULTS_MANAGE';
ALTER TYPE "IDENTITY"."PERMISSION" ADD VALUE 'ANALYSIS_OVERRIDE_MODELS';
