package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.time.Instant;

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
        Instant endedAt) {}
