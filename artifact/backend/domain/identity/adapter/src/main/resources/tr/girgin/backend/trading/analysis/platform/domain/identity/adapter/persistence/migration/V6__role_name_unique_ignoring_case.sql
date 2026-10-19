-- Identity domain, schema "IDENTITY". Flyway versions are per domain.
-- Names are uppercase and quoted, and every name that can carry a schema does.
-- Role names are unique ignoring case, like usernames, so a custom role "admin" cannot sit next to "ADMIN".
-- LOWER folds non-ASCII letters by the database's locale, so the core only allows ASCII role names.
ALTER TABLE "IDENTITY"."ROLES" DROP CONSTRAINT "ROLES_NAME_UK";
CREATE UNIQUE INDEX "ROLES_NAME_LOWER_UK" ON "IDENTITY"."ROLES" (LOWER("NAME"));
