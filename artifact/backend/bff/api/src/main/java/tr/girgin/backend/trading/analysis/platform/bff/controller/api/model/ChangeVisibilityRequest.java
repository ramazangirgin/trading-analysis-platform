package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import jakarta.validation.constraints.NotNull;

public record ChangeVisibilityRequest(
        @NotNull VisibilityEnumDto visibility, @NotNull Long version) {}
