package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A run found in the data dir that the platform did not start (CLI, a third-party UI, an older
 * platform install). Only what the files tell is known; a null decision means it never finished.
 */
public record ExternalAnalysis(
        String ticker,
        LocalDate tradeDate,
        List<Analyst> analysts,
        Rating rating,
        String decision,
        Instant finishedAt) {

    public ExternalAnalysis {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(tradeDate, "tradeDate");
        Objects.requireNonNull(finishedAt, "finishedAt");
        analysts = List.copyOf(analysts);
    }
}
