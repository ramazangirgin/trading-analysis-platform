package tr.girgin.backend.trading.analysis.platform.domain.report.core.model;

/** Report sections in pipeline order, named as upstream's state keys. */
public enum ReportSection {
    MARKET_REPORT,
    SENTIMENT_REPORT,
    NEWS_REPORT,
    FUNDAMENTALS_REPORT,
    INVESTMENT_PLAN,
    TRADER_INVESTMENT_PLAN,
    FINAL_TRADE_DECISION
}
