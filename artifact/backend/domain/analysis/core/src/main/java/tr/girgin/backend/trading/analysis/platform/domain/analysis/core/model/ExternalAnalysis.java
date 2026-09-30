package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A run found in the data dir that the platform did not start (CLI, a third-party UI, an older
 * platform install). Only what the files tell is known.
 *
 * <p>{@link Origin#REPORT_FILES}: a ticker and date's report files, one record per ticker and date
 * (upstream overwrites the files); a null decision means the run never finished. {@code run} is the
 * run history entry that wrote them, when there is one.
 *
 * <p>{@link Origin#RUN_HISTORY}: a run history entry that left no report files (it failed or was
 * stopped early); one record per run, {@code run} is required.
 */
public record ExternalAnalysis(
        Origin origin,
        String ticker,
        LocalDate tradeDate,
        List<Analyst> analysts,
        Rating rating,
        String decision,
        ExternalRun run,
        Instant finishedAt) {

    public enum Origin { REPORT_FILES, RUN_HISTORY }

    public ExternalAnalysis {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(tradeDate, "tradeDate");
        Objects.requireNonNull(finishedAt, "finishedAt");
        if (origin == Origin.RUN_HISTORY && run == null) {
            throw new IllegalArgumentException("A run history record needs its run");
        }
        analysts = List.copyOf(analysts);
    }

    public static ExternalAnalysis reportFiles(String ticker, LocalDate tradeDate, List<Analyst> analysts,
                                               Rating rating, String decision, ExternalRun run,
                                               Instant finishedAt) {
        return new ExternalAnalysis(Origin.REPORT_FILES, ticker, tradeDate, analysts, rating, decision, run,
                finishedAt);
    }

    public static ExternalAnalysis runHistory(String ticker, LocalDate tradeDate, List<Analyst> analysts,
                                              ExternalRun run) {
        return new ExternalAnalysis(Origin.RUN_HISTORY, ticker, tradeDate, analysts, null, null, run,
                run.endedAt());
    }

    /** Stable identity in the data dir, so a rescan finds the same record. */
    public String ref() {
        return switch (origin) {
            case REPORT_FILES -> "report:" + ticker + "/" + tradeDate;
            case RUN_HISTORY -> "run:" + run.id();
        };
    }
}
