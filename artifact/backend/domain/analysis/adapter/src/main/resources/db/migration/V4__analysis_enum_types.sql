-- Analysis domain. Flyway versions are global across domains: analysis owns V1 and V4.
-- Names are uppercase and quoted, constraints and indexes are named explicitly.
-- Every column that holds a Java enum becomes a PostgreSQL enum type of its own, named like a table
-- (the Java enum's name in upper snake case), with the Java constant names as labels in declaration
-- order. The set of analysts becomes an array of that type. Existing rows are converted in place.
CREATE TYPE "ANALYSIS_STATUS" AS ENUM ('QUEUED', 'RUNNING', 'COMPLETED', 'STOPPED', 'FAILED');
CREATE TYPE "ANALYSIS_SOURCE" AS ENUM ('PLATFORM', 'EXTERNAL');
CREATE TYPE "ASSET_TYPE" AS ENUM ('STOCK', 'CRYPTO');
CREATE TYPE "RATING" AS ENUM ('BUY', 'OVERWEIGHT', 'HOLD', 'UNDERWEIGHT', 'SELL', 'REVIEW');
CREATE TYPE "ANALYST" AS ENUM ('MARKET', 'SOCIAL', 'NEWS', 'FUNDAMENTALS');

COMMENT ON TYPE "ANALYSIS_STATUS" IS 'Mirrors the Java enum AnalysisStatus';
COMMENT ON TYPE "ANALYSIS_SOURCE" IS 'Mirrors the Java enum AnalysisSource';
COMMENT ON TYPE "ASSET_TYPE" IS 'Mirrors the Java enum AssetType';
COMMENT ON TYPE "RATING" IS 'Mirrors the Java enum Rating';
COMMENT ON TYPE "ANALYST" IS 'Mirrors the Java enum Analyst';

-- A value that is not a label fails the migration instead of being dropped. PostgreSQL rebuilds
-- ANALYSES_STATUS_IDX for the new type.
ALTER TABLE "ANALYSES"
    ALTER COLUMN "STATUS" TYPE "ANALYSIS_STATUS" USING "STATUS"::"ANALYSIS_STATUS",
    ALTER COLUMN "SOURCE" TYPE "ANALYSIS_SOURCE" USING "SOURCE"::"ANALYSIS_SOURCE",
    ALTER COLUMN "ASSET_TYPE" TYPE "ASSET_TYPE" USING "ASSET_TYPE"::"ASSET_TYPE",
    ALTER COLUMN "RATING" TYPE "RATING" USING "RATING"::"RATING";

-- ANALYSTS was a comma-separated list ('MARKET,NEWS', in the spec's sorted order, possibly with
-- spaces after the commas). An empty string becomes the empty array. The order is kept as stored.
ALTER TABLE "ANALYSES"
    ALTER COLUMN "ANALYSTS" TYPE "ANALYST"[]
        USING COALESCE(NULLIF(regexp_split_to_array(trim("ANALYSTS"), '\s*,\s*'), '{""}'), '{}')::"ANALYST"[];
