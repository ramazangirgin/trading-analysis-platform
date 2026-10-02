package tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.json;

import java.util.List;

/** Output of {@code ta-runner catalog} (docs/event-protocol.md, section 1), read in snake_case. */
public record CatalogJson(
        String upstreamVersion,
        DefaultsJson defaults,
        List<ProviderJson> providers,
        List<AnalystJson> analysts,
        List<String> assetTypes) {

    public record DefaultsJson(String llmProvider, String deepThinkLlm, String quickThinkLlm) {
    }

    public record ProviderJson(
            String id,
            String apiKeyEnv,
            Boolean customModelAllowed,
            List<ModelJson> quickModels,
            List<ModelJson> deepModels) {
    }

    public record ModelJson(String id, String label) {
    }

    public record AnalystJson(String id, String agent) {
    }
}
