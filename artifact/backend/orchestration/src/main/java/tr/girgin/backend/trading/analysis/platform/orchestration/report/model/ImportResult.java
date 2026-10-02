package tr.girgin.backend.trading.analysis.platform.orchestration.report.model;

/** Counts of one data dir scan: reports found, and what happened to their analysis records. */
public record ImportResult(int scanned, int created, int updated, int unchanged) {
}
