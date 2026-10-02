package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.runner.spec;

import java.util.List;

/** spec.json as ta-runner reads it (docs/event-protocol.md, section 2); written in snake_case. */
public record RunnerSpec(
        String runId,
        String ticker,
        String tradeDate,
        String assetType,
        List<String> analysts,
        String llmProvider,
        String deepThinkLlm,
        String quickThinkLlm,
        int maxDebateRounds,
        int maxRiskDiscussRounds,
        String outputLanguage,
        boolean checkpointEnabled) {}
