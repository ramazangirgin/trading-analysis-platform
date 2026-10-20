package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

/** {@code version} is {@code null} while nothing is stored: the values are then the catalog's defaults. */
public record AnalysisDefaultsDto(
        String llmProvider,
        String deepThinkLlm,
        String quickThinkLlm,
        int maxDebateRounds,
        int maxRiskDiscussRounds,
        Long version) {}
