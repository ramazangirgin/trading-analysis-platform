package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

public record AnalysisDefaultsDto(
        String llmProvider,
        String deepThinkLlm,
        String quickThinkLlm,
        int maxDebateRounds,
        int maxRiskDiscussRounds,
        Long version) {}
