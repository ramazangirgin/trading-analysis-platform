-- Analysis domain. Flyway versions are global across domains, in order of creation.
-- Names are uppercase and quoted, constraints and indexes are named explicitly.
-- VERSION is the optimistic lock: Hibernate adds 1 on every update and writes only when the stored
-- value equals the one the writer read, so a write based on a stale copy fails. Existing rows start at 0.
ALTER TABLE "ANALYSES" ADD COLUMN "VERSION" BIGINT NOT NULL DEFAULT 0;
