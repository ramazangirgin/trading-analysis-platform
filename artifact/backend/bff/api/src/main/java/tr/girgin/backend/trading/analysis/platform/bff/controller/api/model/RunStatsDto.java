package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.math.BigDecimal;

public record RunStatsDto(
        long llmCalls,
        long toolCalls,
        long tokensIn,
        long tokensOut,
        BigDecimal costUsd,
        long elapsedMs) {
}
