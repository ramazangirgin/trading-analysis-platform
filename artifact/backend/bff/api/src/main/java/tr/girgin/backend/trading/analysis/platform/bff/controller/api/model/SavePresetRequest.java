package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code version} is optional and used by updates only: the version the client last read. */
public record SavePresetRequest(
        @NotBlank String name, @NotNull PresetValuesDto values, Long version) {}
