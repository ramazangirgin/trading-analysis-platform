package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.row;

/** One row of the {@code analyses} table. */
public record AnalysisRow(
        String id,
        String ticker,
        String tradeDate,
        String assetType,
        String analysts,
        String llmProvider,
        String deepThinkLlm,
        String quickThinkLlm,
        int maxDebateRounds,
        int maxRiskDiscussRounds,
        String outputLanguage,
        boolean checkpointEnabled,
        String status,
        String source,
        String rating,
        String decision,
        long llmCalls,
        long toolCalls,
        long tokensIn,
        long tokensOut,
        Double costUsd,
        long elapsedMs,
        String createdAt,
        String startedAt,
        String endedAt,
        String errorCode,
        String errorMessage,
        String externalRef,
        String runnerRef) {
}
