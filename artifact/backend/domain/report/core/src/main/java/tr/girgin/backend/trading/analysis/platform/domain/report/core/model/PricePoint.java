package tr.girgin.backend.trading.analysis.platform.domain.report.core.model;

import java.time.LocalDate;

/**
 * A day of the price chart: the close and volume with the moving averages upstream's market
 * analyst reads (close_10_ema, close_50_sma, close_200_sma), computed the same way.
 */
public record PricePoint(LocalDate date, double close, long volume, double ema10, double sma50, double sma200) {
}
