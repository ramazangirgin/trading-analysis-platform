package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import jakarta.validation.constraints.NotBlank;

public record SetSecretRequest(@NotBlank String value) {}
