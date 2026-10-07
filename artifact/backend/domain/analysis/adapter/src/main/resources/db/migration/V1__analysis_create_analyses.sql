-- Analysis domain. Flyway versions are global across domains: analysis owns V1.
-- Names are uppercase and quoted, constraints and indexes are named explicitly.
-- One row per analysis run, started by the platform or imported from the TradingAgents data dir.
CREATE TABLE "ANALYSES" (
    "ID"                      TEXT             NOT NULL,
    "TICKER"                  TEXT             NOT NULL,
    "TRADE_DATE"              DATE             NOT NULL,
    "ASSET_TYPE"              TEXT             NOT NULL,
    "ANALYSTS"                TEXT             NOT NULL,
    "LLM_PROVIDER"            TEXT             NOT NULL,
    "DEEP_THINK_LLM"          TEXT             NOT NULL,
    "QUICK_THINK_LLM"         TEXT             NOT NULL,
    "MAX_DEBATE_ROUNDS"       INTEGER          NOT NULL,
    "MAX_RISK_DISCUSS_ROUNDS" INTEGER          NOT NULL,
    "OUTPUT_LANGUAGE"         TEXT             NOT NULL,
    "CHECKPOINT_ENABLED"      BOOLEAN          NOT NULL,
    "STATUS"                  TEXT             NOT NULL,
    "SOURCE"                  TEXT             NOT NULL,
    "RATING"                  TEXT,
    "DECISION"                TEXT,
    "LLM_CALLS"               BIGINT           NOT NULL DEFAULT 0,
    "TOOL_CALLS"              BIGINT           NOT NULL DEFAULT 0,
    "TOKENS_IN"               BIGINT           NOT NULL DEFAULT 0,
    "TOKENS_OUT"              BIGINT           NOT NULL DEFAULT 0,
    "COST_USD"                DOUBLE PRECISION,
    "ELAPSED_MS"              BIGINT           NOT NULL DEFAULT 0,
    "CREATED_AT"              TIMESTAMPTZ      NOT NULL,
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

CREATE INDEX "ANALYSES_CREATED_AT_IDX" ON "ANALYSES" ("CREATED_AT");
CREATE INDEX "ANALYSES_TICKER_TRADE_DATE_IDX" ON "ANALYSES" ("TICKER", "TRADE_DATE");
CREATE INDEX "ANALYSES_STATUS_IDX" ON "ANALYSES" ("STATUS");
CREATE UNIQUE INDEX "ANALYSES_EXTERNAL_REF_UK" ON "ANALYSES" ("EXTERNAL_REF");
