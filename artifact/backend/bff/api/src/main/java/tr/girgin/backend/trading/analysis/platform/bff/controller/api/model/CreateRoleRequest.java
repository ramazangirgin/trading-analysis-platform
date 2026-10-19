package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateRoleRequest(
        @NotBlank String name, String description, @NotNull List<@NotNull PermissionDto> permissions) {}
