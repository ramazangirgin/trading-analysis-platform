package tr.girgin.backend.trading.analysis.platform;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;

final class TestUsers {

    private TestUsers() {}

    static User withRoleIds(User user, Set<RoleId> roleIds) {
        return copy(user, user.enabled(), user.failedLoginCount(), user.lockedUntil(), roleIds);
    }

    static User withEnabled(User user, boolean enabled) {
        return copy(user, enabled, user.failedLoginCount(), user.lockedUntil(), user.roleIds());
    }

    static User withFailedLogin(User user, int failedLoginCount, Optional<Instant> lockedUntil) {
        return copy(user, user.enabled(), failedLoginCount, lockedUntil, user.roleIds());
    }

    private static User copy(
            User user, boolean enabled, int failedLoginCount, Optional<Instant> lockedUntil, Set<RoleId> roleIds) {
        return new User(
                user.id(),
                user.username(),
                user.passwordHash(),
                enabled,
                user.mustChangePassword(),
                failedLoginCount,
                lockedUntil,
                user.lastLoginAt(),
                user.createdAt(),
                user.updatedAt(),
                roleIds,
                user.version());
    }
}
