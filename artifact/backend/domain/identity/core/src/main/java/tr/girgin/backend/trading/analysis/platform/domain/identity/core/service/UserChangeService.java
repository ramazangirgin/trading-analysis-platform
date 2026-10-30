package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;

/** Copies of a {@link User} with one aspect changed, and new user IDs. */
@Service
class UserChangeService {

    UserId newId() {
        return new UserId(UUID.randomUUID().toString());
    }

    User withRoleIds(User user, Set<RoleId> roleIds) {
        return new User(
                user.id(),
                user.username(),
                user.passwordHash(),
                user.enabled(),
                user.mustChangePassword(),
                user.failedLoginCount(),
                user.lockedUntil(),
                user.lastLoginAt(),
                user.createdAt(),
                user.updatedAt(),
                Set.copyOf(roleIds),
                user.version());
    }

    User withEnabled(User user, boolean enabled) {
        return new User(
                user.id(),
                user.username(),
                user.passwordHash(),
                enabled,
                user.mustChangePassword(),
                user.failedLoginCount(),
                user.lockedUntil(),
                user.lastLoginAt(),
                user.createdAt(),
                user.updatedAt(),
                user.roleIds(),
                user.version());
    }

    User withPassword(User user, PasswordHash passwordHash, boolean mustChangePassword) {
        return new User(
                user.id(),
                user.username(),
                passwordHash,
                user.enabled(),
                mustChangePassword,
                user.failedLoginCount(),
                user.lockedUntil(),
                user.lastLoginAt(),
                user.createdAt(),
                user.updatedAt(),
                user.roleIds(),
                user.version());
    }

    User withFailedLogin(User user, int failedLoginCount, Optional<Instant> lockedUntil) {
        return new User(
                user.id(),
                user.username(),
                user.passwordHash(),
                user.enabled(),
                user.mustChangePassword(),
                failedLoginCount,
                lockedUntil,
                user.lastLoginAt(),
                user.createdAt(),
                user.updatedAt(),
                user.roleIds(),
                user.version());
    }

    User withLastLogin(User user, Instant at) {
        return new User(
                user.id(),
                user.username(),
                user.passwordHash(),
                user.enabled(),
                user.mustChangePassword(),
                user.failedLoginCount(),
                user.lockedUntil(),
                Optional.of(at),
                user.createdAt(),
                user.updatedAt(),
                user.roleIds(),
                user.version());
    }
}
