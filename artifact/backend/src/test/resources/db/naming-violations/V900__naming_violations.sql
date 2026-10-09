-- Test fixture for DatabaseNamingCheckTest, never applied anywhere else: it lives outside
-- db/migration on purpose. One violation per rule of backend-database-naming.md, and one correct
-- table that must produce none.

-- Unquoted, so PostgreSQL folds the table and its column to lower case.
CREATE TABLE orders (id TEXT NOT NULL);

-- Quoted table with a lower-case column.
CREATE TABLE "NAMING_LOWER_COLUMN" (
    "ID"     TEXT NOT NULL,
    "ticker" TEXT NOT NULL,
    CONSTRAINT "NAMING_LOWER_COLUMN_PK" PRIMARY KEY ("ID")
);

-- Inline constraints and an unnamed index: PostgreSQL generates _pkey, _key, _fkey and _idx.
CREATE TABLE "NAMING_GENERATED" (
    "ID"          TEXT NOT NULL PRIMARY KEY,
    "CODE"        TEXT NOT NULL UNIQUE,
    "ANALYSIS_ID" TEXT NOT NULL REFERENCES "ANALYSES" ("ID"),
    "TICKER"      TEXT NOT NULL
);
CREATE INDEX ON "NAMING_GENERATED" ("TICKER");

-- Explicit, uppercase, but wrong names; the check constraint has no rule yet.
CREATE TABLE "NAMING_WRONG" (
    "ID"          TEXT NOT NULL,
    "CODE"        TEXT NOT NULL,
    "ANALYSIS_ID" TEXT NOT NULL,
    "TICKER"      TEXT NOT NULL,
    "KIND"        TEXT NOT NULL,
    CONSTRAINT "NAMING_WRONG_KEY" PRIMARY KEY ("ID"),
    CONSTRAINT "NAMING_WRONG_CODE_FK" FOREIGN KEY ("ANALYSIS_ID") REFERENCES "ANALYSES" ("ID"),
    CONSTRAINT "NAMING_WRONG_CODE_UQ" UNIQUE ("CODE"),
    CONSTRAINT "NAMING_WRONG_TICKER_CHECK" CHECK (LENGTH("TICKER") > 0)
);
CREATE UNIQUE INDEX "NAMING_WRONG_TICKER_IDX" ON "NAMING_WRONG" ("TICKER");
CREATE INDEX "NAMING_GENERATED_KIND_IDX" ON "NAMING_WRONG" ("KIND");

-- Unquoted enum type.
CREATE TYPE mood AS ENUM ('HAPPY', 'SAD');

-- The convention kept: NOT NULL columns, named constraints and index, an uppercase enum type.
CREATE TYPE "NAMING_MOOD" AS ENUM ('HAPPY', 'SAD');
CREATE TABLE "NAMING_CORRECT" (
    "ID"          TEXT         NOT NULL,
    "ANALYSIS_ID" TEXT         NOT NULL,
    "CODE"        TEXT         NOT NULL,
    "TICKER"      TEXT         NOT NULL,
    "MOOD"        "NAMING_MOOD" NOT NULL,
    CONSTRAINT "NAMING_CORRECT_PK" PRIMARY KEY ("ID"),
    CONSTRAINT "NAMING_CORRECT_ANALYSIS_ID_FK" FOREIGN KEY ("ANALYSIS_ID") REFERENCES "ANALYSES" ("ID"),
    CONSTRAINT "NAMING_CORRECT_CODE_UK" UNIQUE ("CODE")
);
CREATE INDEX "NAMING_CORRECT_TICKER_IDX" ON "NAMING_CORRECT" ("TICKER");
