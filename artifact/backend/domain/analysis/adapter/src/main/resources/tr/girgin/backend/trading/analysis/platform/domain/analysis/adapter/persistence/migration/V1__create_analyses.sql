-- Analysis domain: everything here lives in the "ANALYSIS" schema, which Flyway creates. Flyway versions
-- are per domain: analysis starts at V1 and has its own history table "ANALYSIS"."FLYWAY_SCHEMA_HISTORY".
-- Names are uppercase and quoted, constraints and indexes are named explicitly, and every name that can
-- carry a schema does (an index and a constraint live in their table's schema).
-- Every column that holds a Java enum is a PostgreSQL enum type of its own, named like a table (the Java
-- enum's name in upper snake case), with the Java constant names as labels in declaration order. The set
-- of analysts is an array of that type.
CREATE TYPE "ANALYSIS"."ANALYSIS_STATUS" AS ENUM ('QUEUED', 'RUNNING', 'COMPLETED', 'STOPPED', 'FAILED');
CREATE TYPE "ANALYSIS"."ANALYSIS_SOURCE" AS ENUM ('PLATFORM', 'EXTERNAL');
CREATE TYPE "ANALYSIS"."ASSET_TYPE" AS ENUM ('STOCK', 'CRYPTO');
CREATE TYPE "ANALYSIS"."RATING" AS ENUM ('BUY', 'OVERWEIGHT', 'HOLD', 'UNDERWEIGHT', 'SELL', 'REVIEW');
CREATE TYPE "ANALYSIS"."ANALYST" AS ENUM ('MARKET', 'SOCIAL', 'NEWS', 'FUNDAMENTALS');

COMMENT ON TYPE "ANALYSIS"."ANALYSIS_STATUS" IS 'Mirrors the Java enum AnalysisStatus';
COMMENT ON TYPE "ANALYSIS"."ANALYSIS_SOURCE" IS 'Mirrors the Java enum AnalysisSource';
COMMENT ON TYPE "ANALYSIS"."ASSET_TYPE" IS 'Mirrors the Java enum AssetType';
COMMENT ON TYPE "ANALYSIS"."RATING" IS 'Mirrors the Java enum Rating';
COMMENT ON TYPE "ANALYSIS"."ANALYST" IS 'Mirrors the Java enum Analyst';

-- One row per analysis run, started by the platform or imported from the TradingAgents data dir.
CREATE TABLE "ANALYSIS"."ANALYSES" (
    "ID"                      TEXT                        NOT NULL,
    "TICKER"                  TEXT                        NOT NULL,
    "TRADE_DATE"              DATE                        NOT NULL,
    "ASSET_TYPE"              "ANALYSIS"."ASSET_TYPE"     NOT NULL,
    "ANALYSTS"                "ANALYSIS"."ANALYST"[]      NOT NULL,
    "LLM_PROVIDER"            TEXT                        NOT NULL,
    "DEEP_THINK_LLM"          TEXT                        NOT NULL,
    "QUICK_THINK_LLM"         TEXT                        NOT NULL,
    "MAX_DEBATE_ROUNDS"       INTEGER                     NOT NULL,
    "MAX_RISK_DISCUSS_ROUNDS" INTEGER                     NOT NULL,
    "OUTPUT_LANGUAGE"         TEXT                        NOT NULL,
    "CHECKPOINT_ENABLED"      BOOLEAN                     NOT NULL,
    "STATUS"                  "ANALYSIS"."ANALYSIS_STATUS" NOT NULL,
    "SOURCE"                  "ANALYSIS"."ANALYSIS_SOURCE" NOT NULL,
    "RATING"                  "ANALYSIS"."RATING",
    "DECISION"                TEXT,
    "LLM_CALLS"               BIGINT                      NOT NULL DEFAULT 0,
    "TOOL_CALLS"              BIGINT                      NOT NULL DEFAULT 0,
    "TOKENS_IN"               BIGINT                      NOT NULL DEFAULT 0,
    "TOKENS_OUT"              BIGINT                      NOT NULL DEFAULT 0,
    "COST_USD"                DOUBLE PRECISION,
    "ELAPSED_MS"              BIGINT                      NOT NULL DEFAULT 0,
    "CREATED_AT"              TIMESTAMPTZ                 NOT NULL,
    "STARTED_AT"              TIMESTAMPTZ,
    "ENDED_AT"                TIMESTAMPTZ,
    "ERROR_CODE"              TEXT,
    "ERROR_MESSAGE"           TEXT,
    -- Where an EXTERNAL record came from in the data dir, so a rescan finds it again:
    -- 'report:<TICKER>/<DATE>' for a ticker/date's report files, 'run:<id>' for a runs.json entry
    -- that left no report. NULL for runs the platform started.
    "EXTERNAL_REF"            TEXT,
    -- The runner's reference to a started run (a process id, later a container id), so a restarted
    -- platform can find the run again and follow or reconcile it.
    "RUNNER_REF"              TEXT,
    CONSTRAINT "ANALYSES_PK" PRIMARY KEY ("ID")
);

CREATE INDEX "ANALYSES_CREATED_AT_IDX" ON "ANALYSIS"."ANALYSES" ("CREATED_AT");
CREATE INDEX "ANALYSES_TICKER_TRADE_DATE_IDX" ON "ANALYSIS"."ANALYSES" ("TICKER", "TRADE_DATE");
CREATE INDEX "ANALYSES_STATUS_IDX" ON "ANALYSIS"."ANALYSES" ("STATUS");
CREATE UNIQUE INDEX "ANALYSES_EXTERNAL_REF_UK" ON "ANALYSIS"."ANALYSES" ("EXTERNAL_REF");
