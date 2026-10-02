package tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.mapper.CatalogJsonToCatalogMapperImpl;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.mapper.VersionJsonToEngineVersionMapperImpl;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.exception.CatalogUnavailableException;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Catalog;

class TaRunnerEngineInfoAdapterTest {

    @TempDir
    private Path dir;

    @Test
    @SuppressWarnings("checkstyle:LineLength") // runner protocol lines in a fixture stay on one line
    void readsTheCatalogAndVersion() throws Exception {
        TaRunnerEngineInfoAdapter adapter = adapter("""
                case "$1" in
                  catalog) echo '{"upstream_version":"0.5.1","defaults":{"llm_provider":"deepseek","deep_think_llm":"deepseek-v4-pro","quick_think_llm":"deepseek-v4-flash"},"providers":[{"id":"deepseek","api_key_env":"DEEPSEEK_API_KEY","custom_model_allowed":true,"quick_models":[{"id":"deepseek-v4-flash","label":"Flash"}],"deep_models":[{"id":"deepseek-v4-pro","label":"Pro"}],"future_field":1}],"analysts":[{"id":"market","agent":"Market Analyst"}],"asset_types":["stock","crypto"]}' ;;
                  version) echo '{"runner_version":"0.1.0","protocol_version":1,"upstream_version":"0.5.1"}' ;;
                esac
                echo "import noise" >&2
                """);

        Catalog catalog = adapter.fetchCatalog();

        assertThat(catalog.upstreamVersion()).isEqualTo("0.5.1");
        assertThat(catalog.defaults().llmProvider()).isEqualTo("deepseek");
        assertThat(catalog.providers()).singleElement().satisfies(p -> {
            assertThat(p.apiKeyEnv()).isEqualTo("DEEPSEEK_API_KEY");
            assertThat(p.customModelAllowed()).isTrue();
            assertThat(p.quickModels()).extracting(m -> m.id()).containsExactly("deepseek-v4-flash");
        });
        assertThat(catalog.analysts()).extracting(a -> a.agent()).containsExactly("Market Analyst");
        assertThat(catalog.assetTypes()).containsExactly("stock", "crypto");
        assertThat(adapter.fetchVersion().protocolVersion()).isEqualTo(1);
    }

    @Test
    void reportsAFailingRunnerAsUnavailable() throws Exception {
        TaRunnerEngineInfoAdapter adapter = adapter("echo 'ModuleNotFoundError' >&2; exit 1\n");

        assertThatThrownBy(adapter::fetchCatalog)
                .isInstanceOf(CatalogUnavailableException.class)
                .hasMessageContaining("exited with code 1");
    }

    @Test
    void reportsGarbageOutputAsUnavailable() throws Exception {
        assertThatThrownBy(adapter("echo 'not json'\n")::fetchVersion)
                .isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void reportsAMissingRunnerAsUnavailable() {
        TaRunnerEngineInfoAdapter adapter = new TaRunnerEngineInfoAdapter(new CatalogJsonToCatalogMapperImpl(),
                new VersionJsonToEngineVersionMapperImpl(), new String[] {dir.resolve("nope").toString()}, dir);

        assertThatThrownBy(adapter::fetchCatalog).isInstanceOf(CatalogUnavailableException.class);
    }

    private TaRunnerEngineInfoAdapter adapter(String script) throws Exception {
        Path file = dir.resolve("runner.sh");
        Files.writeString(file, "#!/bin/sh\n" + script);
        return new TaRunnerEngineInfoAdapter(new CatalogJsonToCatalogMapperImpl(),
                new VersionJsonToEngineVersionMapperImpl(), new String[] {"/bin/sh", file.toString()}, dir);
    }
}
