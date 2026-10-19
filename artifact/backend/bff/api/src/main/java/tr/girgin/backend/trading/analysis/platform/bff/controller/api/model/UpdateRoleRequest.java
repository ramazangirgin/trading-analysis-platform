package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateRoleRequest(
        String name, String description, List<@NotNull PermissionDto> permissions, Long version) {}
