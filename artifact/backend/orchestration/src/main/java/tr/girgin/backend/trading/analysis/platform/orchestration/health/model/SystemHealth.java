package tr.girgin.backend.trading.analysis.platform.orchestration.health.model;

import java.util.List;

/** {@code overall} is the worst of the checks. */
public record SystemHealth(List<HealthCheck> checks, HealthStatus overall) {}
