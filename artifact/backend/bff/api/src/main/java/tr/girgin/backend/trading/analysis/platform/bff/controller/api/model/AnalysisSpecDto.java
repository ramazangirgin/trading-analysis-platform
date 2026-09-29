package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.time.LocalDate;
import java.util.List;

public record AnalysisSpecDto(
        String ticker,
        LocalDate tradeDate,
        AssetTypeDto assetType,
        List<AnalystDto> analysts,
        String llmProvider,
        String deepThinkLlm,
        String quickThinkLlm,
        int maxDebateRounds,
        int maxRiskDiscussRounds,
        String outputLanguage,
        boolean checkpointEnabled) {
}
