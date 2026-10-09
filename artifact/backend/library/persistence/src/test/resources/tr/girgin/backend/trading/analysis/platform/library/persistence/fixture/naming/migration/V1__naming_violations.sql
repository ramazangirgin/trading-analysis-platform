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
    "ticker" TEXT NOT NULL,
    CONSTRAINT "NAMING_LOWER_COLUMN_PK" PRIMARY KEY ("ID")
);

-- Inline constraints and an unnamed index: PostgreSQL generates _pkey, _key, _fkey and _idx.
CREATE TABLE "SAMPLE_NAMING"."NAMING_GENERATED" (
    "ID"          TEXT NOT NULL PRIMARY KEY,
    "CODE"        TEXT NOT NULL UNIQUE,
    "ANALYSIS_ID" TEXT NOT NULL REFERENCES "SAMPLE_NAMING"."NAMING_PARENT" ("ID"),
    "TICKER"      TEXT NOT NULL
);
CREATE INDEX ON "SAMPLE_NAMING"."NAMING_GENERATED" ("TICKER");

-- Explicit, uppercase, but wrong names; the check constraint has no rule yet.
CREATE TABLE "SAMPLE_NAMING"."NAMING_WRONG" (
    "ID"          TEXT NOT NULL,
    "CODE"        TEXT NOT NULL,
    "ANALYSIS_ID" TEXT NOT NULL,
    "TICKER"      TEXT NOT NULL,
    "KIND"        TEXT NOT NULL,
    CONSTRAINT "NAMING_WRONG_KEY" PRIMARY KEY ("ID"),
    CONSTRAINT "NAMING_WRONG_CODE_FK" FOREIGN KEY ("ANALYSIS_ID") REFERENCES "SAMPLE_NAMING"."NAMING_PARENT" ("ID"),
    CONSTRAINT "NAMING_WRONG_CODE_UQ" UNIQUE ("CODE"),
    CONSTRAINT "NAMING_WRONG_TICKER_CHECK" CHECK (LENGTH("TICKER") > 0)
);
CREATE UNIQUE INDEX "NAMING_WRONG_TICKER_IDX" ON "SAMPLE_NAMING"."NAMING_WRONG" ("TICKER");
CREATE INDEX "NAMING_GENERATED_KIND_IDX" ON "SAMPLE_NAMING"."NAMING_WRONG" ("KIND");

-- Unquoted enum type.
CREATE TYPE "SAMPLE_NAMING".mood AS ENUM ('HAPPY', 'SAD');

-- The convention kept: NOT NULL columns, named constraints and index, an uppercase enum type.
CREATE TYPE "SAMPLE_NAMING"."NAMING_MOOD" AS ENUM ('HAPPY', 'SAD');
CREATE TABLE "SAMPLE_NAMING"."NAMING_CORRECT" (
    "ID"          TEXT                         NOT NULL,
    "ANALYSIS_ID" TEXT                         NOT NULL,
    "CODE"        TEXT                         NOT NULL,
    "TICKER"      TEXT                         NOT NULL,
    "MOOD"        "SAMPLE_NAMING"."NAMING_MOOD" NOT NULL,
    CONSTRAINT "NAMING_CORRECT_PK" PRIMARY KEY ("ID"),
    CONSTRAINT "NAMING_CORRECT_ANALYSIS_ID_FK" FOREIGN KEY ("ANALYSIS_ID") REFERENCES "SAMPLE_NAMING"."NAMING_PARENT" ("ID"),
    CONSTRAINT "NAMING_CORRECT_CODE_UK" UNIQUE ("CODE")
);
CREATE INDEX "NAMING_CORRECT_TICKER_IDX" ON "SAMPLE_NAMING"."NAMING_CORRECT" ("TICKER");
