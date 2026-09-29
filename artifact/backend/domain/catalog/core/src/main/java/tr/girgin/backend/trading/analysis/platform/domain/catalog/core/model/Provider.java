package tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model;

import java.util.List;
import java.util.Objects;

/**
 * An LLM provider. {@code apiKeyEnv} is null for keyless providers (Ollama, Bedrock);
 * {@code customModelAllowed} means any model id may be typed in, beyond the listed ones.
 */
public record Provider(
        String id,
        String apiKeyEnv,
        boolean customModelAllowed,
        List<ModelOption> quickModels,
        List<ModelOption> deepModels) {

    public Provider {
        Objects.requireNonNull(id, "id");
        quickModels = List.copyOf(quickModels);
        deepModels = List.copyOf(deepModels);
    }
}
