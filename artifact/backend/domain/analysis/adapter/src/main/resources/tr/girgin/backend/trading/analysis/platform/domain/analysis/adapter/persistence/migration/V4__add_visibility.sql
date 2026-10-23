-- Analysis domain, schema "ANALYSIS". Flyway versions are per domain.
-- Names are uppercase and quoted, and every name that can carry a schema does.
-- Existing analyses stay private. VISIBILITY_CHANGED_AT is null until the first change.
CREATE TYPE "ANALYSIS"."VISIBILITY" AS ENUM ('PRIVATE', 'PUBLIC');
ALTER TABLE "ANALYSIS"."ANALYSES" ADD COLUMN "VISIBILITY" "ANALYSIS"."VISIBILITY" NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE "ANALYSIS"."ANALYSES" ADD COLUMN "VISIBILITY_CHANGED_AT" TIMESTAMPTZ;
COMMENT ON COLUMN "ANALYSIS"."ANALYSES"."VISIBILITY" IS 'PUBLIC: every user who may read analyses sees it, read-only.';
COMMENT ON COLUMN "ANALYSIS"."ANALYSES"."VISIBILITY_CHANGED_AT" IS 'When the owner (or an admin) last made the analysis public or private; a domain fact, not an audit field.';
CREATE INDEX "ANALYSES_VISIBILITY_IDX" ON "ANALYSIS"."ANALYSES" ("VISIBILITY");
