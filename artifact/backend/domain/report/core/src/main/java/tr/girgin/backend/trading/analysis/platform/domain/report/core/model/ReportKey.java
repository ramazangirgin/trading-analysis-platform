package tr.girgin.backend.trading.analysis.platform.domain.report.core.model;

import java.time.LocalDate;

/**
 * A report is identified by ticker and trade date, as upstream lays out its results directory.
 * The ticker is a directory name in the data dir: {@code ReportKeyService} checks its format.
 */
public record ReportKey(String ticker, LocalDate tradeDate) {}
