package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.time.Instant;
import java.util.List;

public record UserDto(
        String id,
        String username,
        List<UserRoleDto> roles,
        boolean enabled,
        boolean mustChangePassword,
        boolean locked,
        Instant lockedUntil,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt,
        Long version) {}
