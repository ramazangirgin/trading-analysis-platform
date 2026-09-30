package tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.dockerjava.api.DockerClient;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Catalog;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.CatalogUnavailableException;

/**
 * Against a real Docker Engine API and the real ta-runner image ({@code docker build -t
 * ta-runner:0.1.0 artifact/ta-runner}). Skipped when there is no engine or no image.
 */
class DockerEngineInfoAdapterTest {

    private static final String HOST = System.getenv().getOrDefault("DOCKER_HOST", "unix:///var/run/docker.sock");
    private static final String IMAGE = System.getenv().getOrDefault("TA_RUNNER_IMAGE", "ta-runner:0.1.0");

    private static DockerClient docker;

    @BeforeAll
    static void needsTheRunnerImage() {
        docker = DockerClients.create(HOST, Duration.ofMinutes(3));
        try {
            docker.inspectImageCmd(IMAGE).exec();
        } catch (RuntimeException e) {
            assumeTrue(false, "No " + IMAGE + " at " + HOST + ": " + e.getMessage());
        }
    }

    @Test
    void readsTheCatalogAndVersionFromTheImage() {
        DockerEngineInfoAdapter adapter = adapter(IMAGE);

        Catalog catalog = adapter.fetchCatalog();

        assertThat(catalog.upstreamVersion()).isEqualTo("0.5.1");
        assertThat(catalog.providers()).extracting(p -> p.id()).contains("deepseek", "openai", "anthropic");
        assertThat(adapter.fetchVersion().protocolVersion()).isEqualTo(1);
    }

    @Test
    void reportsAMissingImageAsUnavailable() {
        assertThatThrownBy(adapter("ta-runner-missing:none")::fetchVersion)
                .isInstanceOf(CatalogUnavailableException.class)
                .hasMessageContaining("ta-runner-missing:none");
    }

    private static DockerEngineInfoAdapter adapter(String image) {
        return new DockerEngineInfoAdapter(new CatalogJsonToCatalogMapperImpl(),
                new VersionJsonToEngineVersionMapperImpl(), docker, image);
    }
}
