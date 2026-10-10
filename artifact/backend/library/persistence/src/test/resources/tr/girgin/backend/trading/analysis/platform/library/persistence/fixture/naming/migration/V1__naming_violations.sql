-- Test fixture for DatabaseNamingCheckTest, in the schema "SAMPLE_NAMING". One violation per rule of
-- backend-database-naming.md, and one correct table that must produce none.

-- The table the foreign keys point to.
CREATE TABLE "SAMPLE_NAMING"."NAMING_PARENT" (
    "ID" TEXT NOT NULL,
    CONSTRAINT "NAMING_PARENT_PK" PRIMARY KEY ("ID")
);

-- Unquoted, so PostgreSQL folds the table and its column to lower case.
CREATE TABLE "SAMPLE_NAMING".orders (id TEXT NOT NULL);

-- Quoted table with a lower-case column.
CREATE TABLE "SAMPLE_NAMING"."NAMING_LOWER_COLUMN" (
    "ID"     TEXT NOT NULL,
    "label"  TEXT NOT NULL,
    CONSTRAINT "NAMING_LOWER_COLUMN_PK" PRIMARY KEY ("ID")
);

-- Inline constraints and an unnamed index: PostgreSQL generates _pkey, _key, _fkey and _idx.
CREATE TABLE "SAMPLE_NAMING"."NAMING_GENERATED" (
    "ID"          TEXT NOT NULL PRIMARY KEY,
    "CODE"        TEXT NOT NULL UNIQUE,
    "PARENT_ID"   TEXT NOT NULL REFERENCES "SAMPLE_NAMING"."NAMING_PARENT" ("ID"),
    "LABEL"       TEXT NOT NULL
);
CREATE INDEX ON "SAMPLE_NAMING"."NAMING_GENERATED" ("LABEL");

-- Explicit, uppercase, but wrong names; the check constraint has no rule yet.
CREATE TABLE "SAMPLE_NAMING"."NAMING_WRONG" (
    "ID"          TEXT NOT NULL,
    "CODE"        TEXT NOT NULL,
    "PARENT_ID"   TEXT NOT NULL,
    "LABEL"       TEXT NOT NULL,
    "KIND"        TEXT NOT NULL,
    CONSTRAINT "NAMING_WRONG_KEY" PRIMARY KEY ("ID"),
    CONSTRAINT "NAMING_WRONG_CODE_FK" FOREIGN KEY ("PARENT_ID") REFERENCES "SAMPLE_NAMING"."NAMING_PARENT" ("ID"),
    CONSTRAINT "NAMING_WRONG_CODE_UQ" UNIQUE ("CODE"),
    CONSTRAINT "NAMING_WRONG_LABEL_CHECK" CHECK (LENGTH("LABEL") > 0)
);
CREATE UNIQUE INDEX "NAMING_WRONG_LABEL_IDX" ON "SAMPLE_NAMING"."NAMING_WRONG" ("LABEL");
CREATE INDEX "NAMING_GENERATED_KIND_IDX" ON "SAMPLE_NAMING"."NAMING_WRONG" ("KIND");

-- Unquoted enum type.
CREATE TYPE "SAMPLE_NAMING".mood AS ENUM ('HAPPY', 'SAD');

-- The convention kept: NOT NULL columns, named constraints and index, an uppercase enum type.
CREATE TYPE "SAMPLE_NAMING"."NAMING_MOOD" AS ENUM ('HAPPY', 'SAD');
CREATE TABLE "SAMPLE_NAMING"."NAMING_CORRECT" (
    "ID"          TEXT                         NOT NULL,
    "PARENT_ID"   TEXT                         NOT NULL,
    "CODE"        TEXT                         NOT NULL,
    "LABEL"       TEXT                         NOT NULL,
    "MOOD"        "SAMPLE_NAMING"."NAMING_MOOD" NOT NULL,
    CONSTRAINT "NAMING_CORRECT_PK" PRIMARY KEY ("ID"),
    CONSTRAINT "NAMING_CORRECT_PARENT_ID_FK" FOREIGN KEY ("PARENT_ID") REFERENCES "SAMPLE_NAMING"."NAMING_PARENT" ("ID"),
    CONSTRAINT "NAMING_CORRECT_CODE_UK" UNIQUE ("CODE")
);
CREATE INDEX "NAMING_CORRECT_LABEL_IDX" ON "SAMPLE_NAMING"."NAMING_CORRECT" ("LABEL");
