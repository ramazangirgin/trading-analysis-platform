package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.util.List;
import java.util.Map;

public record SystemHealthDto(String overall, List<HealthCheckDto> checks) {

    /** {@code code} is what the UI translates, filled in from {@code params}. */
    public record HealthCheckDto(String name, String status, String code, Map<String, Object> params) {}
}
