-- Identity domain, schema "IDENTITY". Flyway versions are per domain.
-- Names are uppercase and quoted, and every name that can carry a schema does.
-- LAST_LOGIN_AT is a domain fact: the identity core sets it from its clock on a successful login.
-- Existing users have not logged in since the column exists, so it starts null.
ALTER TABLE "IDENTITY"."USERS" ADD COLUMN "LAST_LOGIN_AT" TIMESTAMPTZ;
COMMENT ON COLUMN "IDENTITY"."USERS"."LAST_LOGIN_AT" IS 'When the user last logged in successfully; null until then.';
