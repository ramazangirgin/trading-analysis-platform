package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.row;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** One row of the {@code analyses} table. */
public record AnalysisRow(
        String id,
        String ticker,
        LocalDate tradeDate,
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
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        String errorCode,
        String errorMessage,
        String externalRef,
        String runnerRef) {}
