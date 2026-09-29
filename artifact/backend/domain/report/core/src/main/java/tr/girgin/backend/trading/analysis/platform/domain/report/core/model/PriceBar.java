package tr.girgin.backend.trading.analysis.platform.domain.report.core.model;

import java.time.LocalDate;
import java.util.Objects;

/** One trading day from upstream's price cache. */
public record PriceBar(LocalDate date, double open, double high, double low, double close, long volume) {

    public PriceBar {
        Objects.requireNonNull(date, "date");
    }
}
