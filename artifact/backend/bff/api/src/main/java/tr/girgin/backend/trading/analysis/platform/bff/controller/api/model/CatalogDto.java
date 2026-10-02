package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.util.List;

public record CatalogDto(
        String upstreamVersion,
        ModelDefaultsDto defaults,
        List<ProviderDto> providers,
        List<AnalystOptionDto> analysts,
        List<String> assetTypes) {

    public record ModelDefaultsDto(String llmProvider, String deepThinkLlm, String quickThinkLlm) {}

    public record ProviderDto(
            String id,
            String apiKeyEnv,
            boolean customModelAllowed,
            List<ModelOptionDto> quickModels,
            List<ModelOptionDto> deepModels) {}

    public record ModelOptionDto(String id, String label) {}

    public record AnalystOptionDto(String id, String agent) {}
}
