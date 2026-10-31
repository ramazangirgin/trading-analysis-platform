package tr.girgin.backend.trading.analysis.platform.domain.report.core.model;

import java.util.List;

/** About a year of daily prices up to the trade date of a report. */
public record PriceHistory(ReportKey key, List<PricePoint> points) {}
