package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code version} is the one the client read; {@code null} only when it read {@code null} (nothing stored yet). */
public record SaveAnalysisDefaultsRequest(
        @NotBlank String llmProvider,
        @NotBlank String deepThinkLlm,
        @NotBlank String quickThinkLlm,
        @NotNull Integer maxDebateRounds,
        @NotNull Integer maxRiskDiscussRounds,
        Long version) {}
