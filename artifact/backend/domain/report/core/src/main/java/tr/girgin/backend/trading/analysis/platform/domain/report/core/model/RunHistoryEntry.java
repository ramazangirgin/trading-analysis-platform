package tr.girgin.backend.trading.analysis.platform.domain.report.core.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * One finished run from a third-party UI's run history ({@code <data-dir>/runs.json}). It knows
 * what report files do not: the models, the usage, the timing and why a run failed. Fields the
 * file leaves out are null ({@code costUsd}, {@code researchDepth}, the models…).
 *
 * @param analysts upstream's analyst keys ({@code market}, {@code social}, {@code news}, {@code fundamentals})
 */
public record RunHistoryEntry(
        String id,
        ReportKey key,
        List<String> analysts,
        Status status,
        String error,
        String llmProvider,
        String deepThinkLlm,
        String quickThinkLlm,
        Integer researchDepth,
        String outputLanguage,
        long llmCalls,
        long toolCalls,
        long tokensIn,
        long tokensOut,
        BigDecimal costUsd,
        Duration elapsed,
        Instant startedAt,
        Instant endedAt) {

    public enum Status { COMPLETED, FAILED, STOPPED }

    public RunHistoryEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(endedAt, "endedAt");
        analysts = List.copyOf(analysts);
    }
}
