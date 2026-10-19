package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.util.List;

public record RoleDto(
        String id,
        String name,
        boolean builtIn,
        String description,
        List<PermissionDto> permissions,
        long userCount,
        Long version) {}
