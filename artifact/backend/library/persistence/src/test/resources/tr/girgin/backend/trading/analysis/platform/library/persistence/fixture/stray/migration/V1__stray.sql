-- Test fixture of DomainMigrationIsolationCheckTest, for a domain whose schema is "SAMPLE": every kind of
-- object that lies outside it, and one correct table.

-- Unqualified: lands in the schema Flyway uses, which is not the domain's.
CREATE TABLE "STRAY_UNQUALIFIED" ("ID" TEXT NOT NULL, CONSTRAINT "STRAY_UNQUALIFIED_PK" PRIMARY KEY ("ID"));
CREATE TYPE "STRAY_MOOD" AS ENUM ('HAPPY');

-- In public.
CREATE TABLE public."STRAY_PUBLIC" ("ID" TEXT NOT NULL, CONSTRAINT "STRAY_PUBLIC_PK" PRIMARY KEY ("ID"));
CREATE SEQUENCE public."STRAY_PUBLIC_SEQ";
CREATE FUNCTION public."STRAY_PUBLIC_FUNCTION"() RETURNS INTEGER LANGUAGE SQL AS 'SELECT 1';

-- A schema of its own.
CREATE SCHEMA "STRAY_SCHEMA";
CREATE TABLE "STRAY_SCHEMA"."STRAY_OWN" ("ID" TEXT NOT NULL, CONSTRAINT "STRAY_OWN_PK" PRIMARY KEY ("ID"));

-- The convention kept.
CREATE TABLE "SAMPLE"."STRAY_CORRECT" ("ID" TEXT NOT NULL, CONSTRAINT "STRAY_CORRECT_PK" PRIMARY KEY ("ID"));
