-- Analysis domain. Flyway versions are global across domains: analysis owns V1.
CREATE TABLE analyses (
    id                      TEXT PRIMARY KEY,
    ticker                  TEXT    NOT NULL,
    trade_date              TEXT    NOT NULL,
    asset_type              TEXT    NOT NULL,
    analysts                TEXT    NOT NULL,
    llm_provider            TEXT    NOT NULL,
    deep_think_llm          TEXT    NOT NULL,
    quick_think_llm         TEXT    NOT NULL,
    max_debate_rounds       INTEGER NOT NULL,
    max_risk_discuss_rounds INTEGER NOT NULL,
    output_language         TEXT    NOT NULL,
    checkpoint_enabled      BOOLEAN NOT NULL,
    status                  TEXT    NOT NULL,
    source                  TEXT    NOT NULL,
    rating                  TEXT,
    decision                TEXT,
    llm_calls               INTEGER NOT NULL DEFAULT 0,
    tool_calls              INTEGER NOT NULL DEFAULT 0,
    tokens_in               INTEGER NOT NULL DEFAULT 0,
    tokens_out              INTEGER NOT NULL DEFAULT 0,
    cost_usd                REAL,
    elapsed_ms              INTEGER NOT NULL DEFAULT 0,
    -- UTC, fixed-width ISO-8601 (yyyy-MM-ddTHH:mm:ss.SSSZ) so text order is time order.
    created_at              TEXT    NOT NULL,
    started_at              TEXT,
    ended_at                TEXT,
    error_code              TEXT,
    error_message           TEXT
);

CREATE INDEX analyses_created_at ON analyses (created_at);
CREATE INDEX analyses_ticker_trade_date ON analyses (ticker, trade_date);
CREATE INDEX analyses_status ON analyses (status);
