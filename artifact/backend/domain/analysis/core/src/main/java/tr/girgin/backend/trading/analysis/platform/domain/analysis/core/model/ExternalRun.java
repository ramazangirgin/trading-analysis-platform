package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.time.Instant;
import java.util.Objects;

/**
 * What a third-party UI's run history ({@code runs.json}) tells about one run: the models, the
 * usage and the timing that report files do not hold. {@code status} is how the run ended.
 */
public record ExternalRun(
        String id,
        AnalysisStatus status,
        String errorMessage,
        String llmProvider,
        String deepThinkLlm,
        String quickThinkLlm,
        int debateRounds,
        String outputLanguage,
        RunStats stats,
        Instant startedAt,
        Instant endedAt) {

    public ExternalRun {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(llmProvider, "llmProvider");
        Objects.requireNonNull(deepThinkLlm, "deepThinkLlm");
        Objects.requireNonNull(quickThinkLlm, "quickThinkLlm");
        Objects.requireNonNull(outputLanguage, "outputLanguage");
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(endedAt, "endedAt");
        if (!status.isTerminal()) {
            throw new IllegalArgumentException("Not a finished run: " + status);
        }
        if (debateRounds < 1) {
            throw new IllegalArgumentException("debateRounds must be at least 1: " + debateRounds);
        }
    }
}
