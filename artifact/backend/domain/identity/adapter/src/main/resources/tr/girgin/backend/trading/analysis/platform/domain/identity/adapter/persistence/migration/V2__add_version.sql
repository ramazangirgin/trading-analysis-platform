-- Identity domain, schema "IDENTITY". Flyway versions are per domain.
-- Names are uppercase and quoted, and every name that can carry a schema does.
-- VERSION is the optimistic lock: Hibernate adds 1 on every update and writes only when the stored
-- value equals the one the writer read, so a write based on a stale copy fails. Existing rows start at 0.
-- USER_ROLES gets no column: it is part of the user aggregate, and a change to it bumps USERS.VERSION.
ALTER TABLE "IDENTITY"."USERS" ADD COLUMN "VERSION" BIGINT NOT NULL DEFAULT 0;
ALTER TABLE "IDENTITY"."ROLES" ADD COLUMN "VERSION" BIGINT NOT NULL DEFAULT 0;
