package tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.json.CatalogJson;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.AnalystOption;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Catalog;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.ModelOption;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Provider;

class CatalogJsonToCatalogMapperTest {

    private final CatalogJsonToCatalogMapper mapper = new CatalogJsonToCatalogMapperImpl();

    @Test
    void missingListsMapToEmptyLists() {
        Catalog catalog = mapper.map(new CatalogJson("1.0", null, null, null, null));

        assertThat(catalog.upstreamVersion()).isEqualTo("1.0");
        assertThat(catalog.providers()).isEmpty();
        assertThat(catalog.analysts()).isEmpty();
        assertThat(catalog.assetTypes()).isEmpty();
    }

    @Test
    void aProviderWithoutModelsHasEmptyModelLists() {
        CatalogJson.ProviderJson provider = new CatalogJson.ProviderJson("openai", "OPENAI_API_KEY", null, null, null);

        Provider mapped = mapper.map(provider);

        assertThat(mapped.customModelAllowed()).isFalse();
        assertThat(mapped.quickModels()).isEmpty();
        assertThat(mapped.deepModels()).isEmpty();
    }

    @Test
    void givenListsAreCopied() {
        CatalogJson json = new CatalogJson(
                "1.0",
                null,
                List.of(new CatalogJson.ProviderJson(
                        "openai",
                        "OPENAI_API_KEY",
                        true,
                        List.of(new CatalogJson.ModelJson("q", "Quick")),
                        List.of(new CatalogJson.ModelJson("d", "Deep")))),
                List.of(new CatalogJson.AnalystJson("market", "Market Analyst")),
                List.of("stock", "crypto"));

        Catalog catalog = mapper.map(json);

        assertThat(catalog.providers()).hasSize(1);
        Provider provider = catalog.providers().get(0);
        assertThat(provider.customModelAllowed()).isTrue();
        assertThat(provider.quickModels()).containsExactly(new ModelOption("q", "Quick"));
        assertThat(provider.deepModels()).containsExactly(new ModelOption("d", "Deep"));
        assertThat(catalog.analysts()).containsExactly(new AnalystOption("market", "Market Analyst"));
        assertThat(catalog.assetTypes()).containsExactly("stock", "crypto");
    }
}
