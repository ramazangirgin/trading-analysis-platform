package tr.girgin.backend.trading.analysis.platform.orchestration.health.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.exception.CatalogUnavailableException;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.inbound.GetCatalogUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Catalog;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.EngineVersion;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.ModelDefaults;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Provider;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound.ManageSecretsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.SecretSource;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.SecretStatus;
import tr.girgin.backend.trading.analysis.platform.orchestration.health.model.HealthCheck;
import tr.girgin.backend.trading.analysis.platform.orchestration.health.model.HealthStatus;
import tr.girgin.backend.trading.analysis.platform.orchestration.health.model.SystemHealth;

class SystemHealthServiceTest {

    private static final Catalog CATALOG = new Catalog("0.5.1", new ModelDefaults("deepseek", "a", "b"),
            List.of(new Provider("deepseek", "DEEPSEEK_API_KEY", true, List.of(), List.of()),
                    new Provider("ollama", null, true, List.of(), List.of())),
            List.of(), List.of("stock"));

    @Test
    void allGood() {
        SystemHealth health = service(new EngineVersion("0.1.0", 1, "0.5.1"), List.of(
                new SecretStatus("DEEPSEEK_API_KEY", SecretSource.EXTERNAL_FILE, "••••d1f0")), 3).checkHealth();

        assertThat(health.overall()).isEqualTo(HealthStatus.UP);
        assertThat(health.checks()).extracting(HealthCheck::code)
                .containsExactly("runner_ok", "provider_keys_ok", "data_dir_ok", "queue_ok");
        assertThat(health.checks().get(1).params()).containsEntry("providers", "deepseek");
    }

    @Test
    void warnsWithoutKeysOrReports() {
        SystemHealth health = service(new EngineVersion("0.1.0", 1, "0.5.1"), List.of(), 0).checkHealth();

        assertThat(health.overall()).isEqualTo(HealthStatus.WARN);
        assertThat(health.checks()).extracting(HealthCheck::code).contains("no_provider_keys", "data_dir_empty");
    }

    @Test
    void aRunnerSpeakingAnotherProtocolIsDown() {
        SystemHealth health = service(new EngineVersion("9.0.0", 2, "0.9.0"), List.of(), 1).checkHealth();

        assertThat(health.overall()).isEqualTo(HealthStatus.DOWN);
        assertThat(health.checks().getFirst().code()).isEqualTo("runner_protocol_mismatch");
    }

    @Test
    void aMissingRunnerIsDown() {
        SystemHealthService service = new SystemHealthService(
                () -> {
                    throw new CatalogUnavailableException("Cannot start ta-runner", null);
                },
                () -> {
                    throw new CatalogUnavailableException("Cannot start ta-runner", null);
                },
                List::of, secrets(List.of()), filter -> List.of());

        assertThat(service.checkHealth().checks().getFirst().code()).isEqualTo("runner_unavailable");
    }

    private static SystemHealthService service(EngineVersion version, List<SecretStatus> secrets, int reports) {
        GetCatalogUseCase catalog = () -> CATALOG;
        return new SystemHealthService(() -> version, catalog,
                () -> java.util.Collections.nCopies(reports, null), secrets(secrets), filter -> List.of());
    }

    private static ManageSecretsUseCase secrets(List<SecretStatus> statuses) {
        return new ManageSecretsUseCase() {
            @Override
            public List<SecretStatus> listSecrets() {
                return statuses;
            }

            @Override
            public SecretStatus setSecret(String name, String value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void removeSecret(String name) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
