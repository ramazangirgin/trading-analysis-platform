-- Analysis domain. Where an EXTERNAL record came from in the data dir, so a rescan finds it again:
-- 'report:<TICKER>/<DATE>' for a ticker/date's report files, 'run:<id>' for a runs.json entry
-- that left no report. NULL for runs the platform started.
ALTER TABLE analyses ADD COLUMN external_ref TEXT;

UPDATE analyses SET external_ref = 'report:' || ticker || '/' || trade_date WHERE source = 'EXTERNAL';

CREATE UNIQUE INDEX analyses_external_ref ON analyses (external_ref);
