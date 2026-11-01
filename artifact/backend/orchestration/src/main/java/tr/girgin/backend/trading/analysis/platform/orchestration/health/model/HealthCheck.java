package tr.girgin.backend.trading.analysis.platform.orchestration.health.model;

import java.util.Map;

/** One diagnostic: a stable name and code the UI translates, with its parameters. */
public record HealthCheck(String name, HealthStatus status, String code, Map<String, Object> params) {}
