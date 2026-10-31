package tr.girgin.backend.trading.analysis.platform.domain.settings.core.model;

/**
 * The platform's LLM provider, models and debate round counts for an analysis.
 *
 * @param version the optimistic lock. Owned by persistence: {@code null} while nothing is stored, set on every record
 *     the repository returns, and never changed by the core.
 */
public record AnalysisDefaults(
        String llmProvider,
        String deepThinkLlm,
        String quickThinkLlm,
        int maxDebateRounds,
        int maxRiskDiscussRounds,
        Long version) {}
