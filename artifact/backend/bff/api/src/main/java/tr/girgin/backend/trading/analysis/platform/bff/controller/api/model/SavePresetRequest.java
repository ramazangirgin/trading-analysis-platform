package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record SavePresetRequest(
        @NotBlank String name, @NotNull Map<String, Object> values) {}
