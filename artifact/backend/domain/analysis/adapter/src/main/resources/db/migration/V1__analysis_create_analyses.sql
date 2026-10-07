-- Analysis domain. Flyway versions are global across domains: analysis owns V1.
-- One row per analysis run, started by the platform or imported from the TradingAgents data dir.
CREATE TABLE analyses (
    id                      TEXT PRIMARY KEY,
    ticker                  TEXT             NOT NULL,
    trade_date              DATE             NOT NULL,
    asset_type              TEXT             NOT NULL,
    analysts                TEXT             NOT NULL,
    llm_provider            TEXT             NOT NULL,
    deep_think_llm          TEXT             NOT NULL,
    quick_think_llm         TEXT             NOT NULL,
    max_debate_rounds       INTEGER          NOT NULL,
    max_risk_discuss_rounds INTEGER          NOT NULL,
    output_language         TEXT             NOT NULL,
    checkpoint_enabled      BOOLEAN          NOT NULL,
    status                  TEXT             NOT NULL,
    source                  TEXT             NOT NULL,
    rating                  TEXT,
    decision                TEXT,
    llm_calls               BIGINT           NOT NULL DEFAULT 0,
    tool_calls              BIGINT           NOT NULL DEFAULT 0,
    tokens_in               BIGINT           NOT NULL DEFAULT 0,
    tokens_out              BIGINT           NOT NULL DEFAULT 0,
    cost_usd                DOUBLE PRECISION,
    elapsed_ms              BIGINT           NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ      NOT NULL,
    started_at              TIMESTAMPTZ,
    ended_at                TIMESTAMPTZ,
    error_code              TEXT,
    error_message           TEXT,
    -- Where an EXTERNAL record came from in the data dir, so a rescan finds it again:
    -- 'report:<TICKER>/<DATE>' for a ticker/date's report files, 'run:<id>' for a runs.json entry
    -- that left no report. NULL for runs the platform started.
    external_ref            TEXT,
    -- The runner's reference to a started run (a process id, later a container id), so a restarted
    -- platform can find the run again and follow or reconcile it.
    runner_ref              TEXT
);

CREATE INDEX analyses_created_at ON analyses (created_at);
CREATE INDEX analyses_ticker_trade_date ON analyses (ticker, trade_date);
CREATE INDEX analyses_status ON analyses (status);
CREATE UNIQUE INDEX analyses_external_ref ON analyses (external_ref);
