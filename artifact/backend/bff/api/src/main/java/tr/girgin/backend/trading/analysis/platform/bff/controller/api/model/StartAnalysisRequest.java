package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record StartAnalysisRequest(
        @NotBlank String ticker,
        @NotNull LocalDate tradeDate,
        AssetTypeDto assetType,
        @NotEmpty List<@NotNull AnalystDto> analysts,
        String llmProvider,
        String deepThinkLlm,
        String quickThinkLlm,
        Integer maxDebateRounds,
        Integer maxRiskDiscussRounds,
        String outputLanguage,
        Boolean checkpointEnabled) {}
