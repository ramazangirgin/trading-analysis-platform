-- Test only (V2AnalysisVersionMigrationTest): rows in the form V1 stored them, before V2 added the
-- VERSION column. It runs between V1 and V2.
INSERT INTO "ANALYSIS"."ANALYSES" ("ID", "TICKER", "TRADE_DATE", "ASSET_TYPE", "ANALYSTS", "LLM_PROVIDER",
                                   "DEEP_THINK_LLM", "QUICK_THINK_LLM", "MAX_DEBATE_ROUNDS",
                                   "MAX_RISK_DISCUSS_ROUNDS", "OUTPUT_LANGUAGE", "CHECKPOINT_ENABLED", "STATUS",
                                   "SOURCE", "RATING", "DECISION", "LLM_CALLS", "TOOL_CALLS", "TOKENS_IN",
                                   "TOKENS_OUT", "COST_USD", "ELAPSED_MS", "CREATED_AT", "STARTED_AT", "ENDED_AT",
                                   "ERROR_CODE", "ERROR_MESSAGE", "EXTERNAL_REF", "RUNNER_REF")
VALUES
    -- A finished platform run.
    ('r_seed_a', 'NVDA', '2026-09-25', 'STOCK', '{NEWS,MARKET}', 'deepseek', 'deepseek-v4-pro', 'deepseek-v4-flash',
     1, 2, 'Turkish', TRUE, 'COMPLETED', 'PLATFORM', 'OVERWEIGHT', 'Rating: Overweight', 12, 10, 77137, 48090, 0.42,
     260500, '2026-09-29 10:00:00.123+00', '2026-09-29 10:00:01+00', '2026-09-29 10:04:21+00', NULL, NULL, NULL,
     '4242'),
    -- An imported run that failed.
    ('r_seed_c', 'AAPL', '2026-09-27', 'STOCK', '{MARKET,SOCIAL,NEWS,FUNDAMENTALS}', 'unknown', 'unknown',
     'unknown', 1, 1, 'English', FALSE, 'FAILED', 'EXTERNAL', 'REVIEW', NULL, 0, 0, 0, 0, NULL, 0,
     '2026-09-27 21:00:00+00', '2026-09-27 21:00:00+00', '2026-09-27 21:34:35.383+00', 'incomplete_report', NULL,
     'report:AAPL/2026-09-27', NULL);
