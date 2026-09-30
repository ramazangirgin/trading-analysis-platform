package tr.girgin.backend.trading.analysis.platform.domain.report.core.model;

/**
 * Report sections in pipeline order, named as upstream's state keys. {@link #DEEP_ANALYSIS} is not
 * upstream's: it is a deep-analysis skill's write-up of a whole run, found beside the data dir.
 */
public enum ReportSection {
    MARKET_REPORT,
    SENTIMENT_REPORT,
    NEWS_REPORT,
    FUNDAMENTALS_REPORT,
    INVESTMENT_PLAN,
    TRADER_INVESTMENT_PLAN,
    FINAL_TRADE_DECISION,
    DEEP_ANALYSIS
}
