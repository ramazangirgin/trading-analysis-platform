-- Analysis domain, schema "ANALYSIS". Flyway versions are per domain.
-- Names are uppercase and quoted, and every name that can carry a schema does.
-- OWNER_ID is nullable: rows from before owners existed have none until the platform gives them to the
-- first admin on startup.
ALTER TABLE "ANALYSIS"."ANALYSES" ADD COLUMN "OWNER_ID" TEXT;
COMMENT ON COLUMN "ANALYSIS"."ANALYSES"."OWNER_ID" IS 'The "IDENTITY"."USERS" ID of the user the analysis belongs to; no foreign key, as no migration references another domain''s schema.';
CREATE INDEX "ANALYSES_OWNER_ID_IDX" ON "ANALYSIS"."ANALYSES" ("OWNER_ID");
