package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

/** LLM usage and wall time of a run. {@code costUsd} is null until pricing is known. */
public record RunStats(
        long llmCalls,
        long toolCalls,
        long tokensIn,
        long tokensOut,
        BigDecimal costUsd,
        Duration elapsed) {

    public static final RunStats EMPTY = new RunStats(0, 0, 0, 0, null, Duration.ZERO);

    public RunStats {
        Objects.requireNonNull(elapsed, "elapsed");
    }
}
