package tr.girgin.backend.trading.analysis.platform.orchestration.health;

import java.util.Map;
import java.util.Objects;

/** One diagnostic: a stable name and code the UI translates, with its parameters. */
public record HealthCheck(String name, HealthStatus status, String code, Map<String, Object> params) {

    public HealthCheck {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(code, "code");
        params = Map.copyOf(params);
    }
}
