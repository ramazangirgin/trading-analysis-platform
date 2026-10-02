package tr.girgin.backend.trading.analysis.platform.orchestration.health.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.ListAnalysesUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.exception.CatalogUnavailableException;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.inbound.GetCatalogUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.inbound.GetEngineVersionUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.EngineVersion;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Provider;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.ScanReportsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.inbound.ManageSecretsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.SecretStatus;
import tr.girgin.backend.trading.analysis.platform.orchestration.health.inbound.SystemHealthUseCase;
import tr.girgin.backend.trading.analysis.platform.orchestration.health.model.HealthCheck;
import tr.girgin.backend.trading.analysis.platform.orchestration.health.model.HealthStatus;
import tr.girgin.backend.trading.analysis.platform.orchestration.health.model.SystemHealth;

/** First-run and troubleshooting checks, across domains (PLAN.md section 3.4). */
@Service
class SystemHealthService implements SystemHealthUseCase {

    static final int SUPPORTED_PROTOCOL = 1;

    private final GetEngineVersionUseCase engineVersion;
    private final GetCatalogUseCase catalog;
    private final ScanReportsUseCase scanReports;
    private final ManageSecretsUseCase secrets;
    private final ListAnalysesUseCase analyses;

    SystemHealthService(GetEngineVersionUseCase engineVersion,
                        GetCatalogUseCase catalog,
                        ScanReportsUseCase scanReports,
                        ManageSecretsUseCase secrets,
                        ListAnalysesUseCase analyses) {
        this.engineVersion = engineVersion;
        this.catalog = catalog;
        this.scanReports = scanReports;
        this.secrets = secrets;
        this.analyses = analyses;
    }

    @Override
    public SystemHealth checkHealth() {
        return new SystemHealth(List.of(runner(), keys(), dataDir(), queue()));
    }

    private HealthCheck runner() {
        try {
            EngineVersion version = engineVersion.getEngineVersion();
            Map<String, Object> params = Map.of("runnerVersion", String.valueOf(version.runnerVersion()),
                    "upstreamVersion", String.valueOf(version.upstreamVersion()),
                    "protocolVersion", version.protocolVersion());
            return version.protocolVersion() == SUPPORTED_PROTOCOL
                    ? new HealthCheck("runner", HealthStatus.UP, "runner_ok", params)
                    : new HealthCheck("runner", HealthStatus.DOWN, "runner_protocol_mismatch", params);
        } catch (CatalogUnavailableException e) {
            return new HealthCheck("runner", HealthStatus.DOWN, "runner_unavailable",
                    Map.of("detail", String.valueOf(e.getMessage())));
        }
    }

    private HealthCheck keys() {
        Set<String> set = secrets.listSecrets().stream().map(SecretStatus::name).collect(Collectors.toSet());
        List<String> usable;
        try {
            usable = catalog.getCatalog().providers().stream()
                    .filter(p -> p.apiKeyEnv() != null && set.contains(p.apiKeyEnv()))
                    .map(Provider::id)
                    .sorted()
                    .toList();
        } catch (CatalogUnavailableException e) {
            usable = set.stream().filter(name -> name.endsWith("_API_KEY")).sorted().toList();
        }
        return usable.isEmpty()
                ? new HealthCheck("keys", HealthStatus.WARN, "no_provider_keys", Map.of())
                : new HealthCheck("keys", HealthStatus.UP, "provider_keys_ok", Map.of("providers", String.join(", ", usable)));
    }

    private HealthCheck dataDir() {
        try {
            int reports = scanReports.scan().size();
            return new HealthCheck("dataDir", reports > 0 ? HealthStatus.UP : HealthStatus.WARN,
                    reports > 0 ? "data_dir_ok" : "data_dir_empty", Map.of("reports", reports));
        } catch (RuntimeException e) {
            return new HealthCheck("dataDir", HealthStatus.DOWN, "data_dir_unreadable",
                    Map.of("detail", String.valueOf(e.getMessage())));
        }
    }

    private HealthCheck queue() {
        long running = analyses.list(new AnalysisFilter(AnalysisStatus.RUNNING, null)).size();
        long queued = analyses.list(new AnalysisFilter(AnalysisStatus.QUEUED, null)).size();
        return new HealthCheck("queue", HealthStatus.UP, "queue_ok", Map.of("running", running, "queued", queued));
    }
}
