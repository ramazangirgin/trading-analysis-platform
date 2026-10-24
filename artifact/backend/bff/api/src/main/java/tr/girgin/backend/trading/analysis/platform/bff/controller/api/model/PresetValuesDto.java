package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** The New Analysis form fields a preset keeps; a field left out was not saved. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PresetValuesDto(
        String assetType,
        List<String> analysts,
        String llmProvider,
        String deepThinkLlm,
        String quickThinkLlm,
        Integer maxDebateRounds,
        Integer maxRiskDiscussRounds,
        String outputLanguage,
        Boolean checkpointEnabled) {}
