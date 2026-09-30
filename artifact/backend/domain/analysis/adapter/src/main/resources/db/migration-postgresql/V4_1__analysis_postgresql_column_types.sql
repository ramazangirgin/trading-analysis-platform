-- Analysis domain, PostgreSQL only (SQLite's REAL and INTEGER are 8 bytes already). In PostgreSQL
-- REAL is a 4-byte float, which would round costs, and INTEGER 4 bytes. Version 4.1 sits between
-- V4 and any later common migration.
ALTER TABLE analyses
    ALTER COLUMN cost_usd TYPE DOUBLE PRECISION,
    ALTER COLUMN llm_calls TYPE BIGINT,
    ALTER COLUMN tool_calls TYPE BIGINT,
    ALTER COLUMN tokens_in TYPE BIGINT,
    ALTER COLUMN tokens_out TYPE BIGINT,
    ALTER COLUMN elapsed_ms TYPE BIGINT;
