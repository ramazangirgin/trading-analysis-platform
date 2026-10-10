-- Analysis domain, schema "ANALYSIS". Flyway versions are per domain.
-- Names are uppercase and quoted, and every name that can carry a schema does.
-- VERSION is the optimistic lock: Hibernate adds 1 on every update and writes only when the stored
-- value equals the one the writer read, so a write based on a stale copy fails. Existing rows start at 0.
ALTER TABLE "ANALYSIS"."ANALYSES" ADD COLUMN "VERSION" BIGINT NOT NULL DEFAULT 0;
