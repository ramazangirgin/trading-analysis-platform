package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * A new analysis. Optional fields default to one debate and one risk round, English output,
 * stock pipeline, no checkpointing. Formats are checked by the analysis domain.
 */
public record StartAnalysisRequest(
        @NotBlank String ticker,
        @NotNull LocalDate tradeDate,
        AssetTypeDto assetType,
        @NotEmpty List<@NotNull AnalystDto> analysts,
        @NotBlank String llmProvider,
        @NotBlank String deepThinkLlm,
        @NotBlank String quickThinkLlm,
        Integer maxDebateRounds,
        Integer maxRiskDiscussRounds,
        String outputLanguage,
        Boolean checkpointEnabled) {}
