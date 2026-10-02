package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

/** The Portfolio Manager's 5-tier rating, or REVIEW when upstream could not parse one. */
public enum Rating {
    BUY,
    OVERWEIGHT,
    HOLD,
    UNDERWEIGHT,
    SELL,
    REVIEW
}
