package tr.girgin.backend.trading.analysis.platform.orchestration.health.model;

import java.util.Comparator;
import java.util.List;

public record SystemHealth(List<HealthCheck> checks) {

    public SystemHealth {
        checks = List.copyOf(checks);
    }

    /** The worst of the checks. */
    public HealthStatus overall() {
        return checks.stream()
                .map(HealthCheck::status)
                .max(Comparator.naturalOrder())
                .orElse(HealthStatus.UP);
    }
}
