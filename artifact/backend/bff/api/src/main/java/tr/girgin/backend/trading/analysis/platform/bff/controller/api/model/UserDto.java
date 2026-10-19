package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.time.Instant;
import java.util.List;

/**
 * A user account, without any password data. {@code roles} are sorted by name. {@code locked} is whether the
 * account is locked now: an expired lock reads as not locked. {@code lockedUntil} and {@code lastLoginAt} are
 * null when unset. {@code version} counts the changes; send it back on an update to fail if someone else changed
 * the user meanwhile.
 */
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
