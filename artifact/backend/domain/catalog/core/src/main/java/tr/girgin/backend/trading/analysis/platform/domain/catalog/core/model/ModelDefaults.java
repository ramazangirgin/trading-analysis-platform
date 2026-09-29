package tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model;

/** Upstream's default provider and models, including its TRADINGAGENTS_* overrides. */
public record ModelDefaults(String llmProvider, String deepThinkLlm, String quickThinkLlm) {
}
