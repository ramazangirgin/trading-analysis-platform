package tr.girgin.backend.trading.analysis.platform.orchestration.analysisstart.model;

import java.time.LocalDate;
import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;

public record RequestedAnalysis(
        String ticker,
        LocalDate tradeDate,
        AssetType assetType,
        List<Analyst> analysts,
        String llmProvider,
        String deepThinkLlm,
        String quickThinkLlm,
        Integer maxDebateRounds,
        Integer maxRiskDiscussRounds,
        String outputLanguage,
        Boolean checkpointEnabled) {}
