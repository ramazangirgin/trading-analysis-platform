package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.time.LocalDate;
import java.util.List;

/** What to analyze and with which models; becomes the runner's spec.json. */
public record AnalysisSpec(
        String ticker,
        LocalDate tradeDate,
        AssetType assetType,
        List<Analyst> analysts,
        String llmProvider,
        String deepThinkLlm,
        String quickThinkLlm,
        int maxDebateRounds,
        int maxRiskDiscussRounds,
        String outputLanguage,
        boolean checkpointEnabled) {}
