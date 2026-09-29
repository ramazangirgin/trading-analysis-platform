package tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner;

import java.util.List;

/** Output of {@code ta-runner catalog} (docs/event-protocol.md, section 1), read in snake_case. */
record CatalogJson(
        String upstreamVersion,
        DefaultsJson defaults,
        List<ProviderJson> providers,
        List<AnalystJson> analysts,
        List<String> assetTypes) {

    record DefaultsJson(String llmProvider, String deepThinkLlm, String quickThinkLlm) {
    }

    record ProviderJson(
            String id,
            String apiKeyEnv,
            Boolean customModelAllowed,
            List<ModelJson> quickModels,
            List<ModelJson> deepModels) {
    }

    record ModelJson(String id, String label) {
    }

    record AnalystJson(String id, String agent) {
    }
}
