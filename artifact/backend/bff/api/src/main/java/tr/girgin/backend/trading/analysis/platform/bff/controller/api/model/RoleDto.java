package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.util.List;

/** A role with what it may do. {@code permissions} are sorted by key. */
public record RoleDto(String id, String name, boolean builtIn, String description, List<PermissionDto> permissions) {}
