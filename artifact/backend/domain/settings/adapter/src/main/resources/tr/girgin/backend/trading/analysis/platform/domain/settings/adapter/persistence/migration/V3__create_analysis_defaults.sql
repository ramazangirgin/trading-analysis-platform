-- Settings domain, schema "SETTINGS". Flyway versions are per domain.
-- Names are uppercase and quoted, and every name that can carry a schema does.
CREATE TABLE "SETTINGS"."ANALYSIS_DEFAULTS" (
    "ID"                      TEXT    NOT NULL,
    "LLM_PROVIDER"            TEXT    NOT NULL,
    "DEEP_THINK_LLM"          TEXT    NOT NULL,
    "QUICK_THINK_LLM"         TEXT    NOT NULL,
    "MAX_DEBATE_ROUNDS"       INTEGER NOT NULL,
    "MAX_RISK_DISCUSS_ROUNDS" INTEGER NOT NULL,
    "VERSION"                 BIGINT  NOT NULL DEFAULT 0,
    CONSTRAINT "ANALYSIS_DEFAULTS_PK" PRIMARY KEY ("ID")
);

COMMENT ON TABLE "SETTINGS"."ANALYSIS_DEFAULTS" IS
    'The platform''s analysis defaults: at most one row, with the fixed ID DEFAULTS. No row means nothing is saved yet.';
