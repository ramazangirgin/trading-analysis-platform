package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.util.List;

/**
 * The signed-in user. {@code roles} are role names, {@code permissions} permission keys (e.g.
 * {@code analysis:read:all}), both sorted.
 */
public record CurrentUserDto(
        String id, String username, List<String> roles, List<PermissionDto> permissions, boolean mustChangePassword) {}
