package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;

final class IdentityCopies {

    private IdentityCopies() {}

    static RoleId newRoleId() {
        return new RoleId(UUID.randomUUID().toString());
    }

    static Role withPermissions(Role role, Set<Permission> permissions) {
        return new Role(role.id(), role.name(), role.builtIn(), role.description(), permissions, role.version());
    }

    static Role withName(Role role, String name) {
        return new Role(role.id(), name, role.builtIn(), role.description(), role.permissions(), role.version());
    }

    static User withRoleIds(User user, Set<RoleId> roleIds) {
        return copy(user, user.enabled(), user.failedLoginCount(), user.lockedUntil(), user.lastLoginAt(), roleIds);
    }

    static User withEnabled(User user, boolean enabled) {
        return copy(user, enabled, user.failedLoginCount(), user.lockedUntil(), user.lastLoginAt(), user.roleIds());
    }

    static User withFailedLogin(User user, int failedLoginCount, Optional<Instant> lockedUntil) {
        return copy(user, user.enabled(), failedLoginCount, lockedUntil, user.lastLoginAt(), user.roleIds());
    }

    static User withLastLogin(User user, Instant at) {
        return copy(user, user.enabled(), user.failedLoginCount(), user.lockedUntil(), Optional.of(at), user.roleIds());
    }

    private static User copy(
            User user,
            boolean enabled,
            int failedLoginCount,
            Optional<Instant> lockedUntil,
            Optional<Instant> lastLoginAt,
            Set<RoleId> roleIds) {
        return new User(
                user.id(),
                user.username(),
                user.passwordHash(),
                enabled,
                user.mustChangePassword(),
                failedLoginCount,
                lockedUntil,
                lastLoginAt,
                user.createdAt(),
                user.updatedAt(),
                roleIds,
                user.version());
    }
}
