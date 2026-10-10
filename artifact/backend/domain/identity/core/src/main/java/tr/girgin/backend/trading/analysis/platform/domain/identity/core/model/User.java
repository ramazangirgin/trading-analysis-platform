package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A person who signs in, with the roles assigned to them.
 *
 * @param createdAt when the user was first saved. An audit field: persistence sets it, so it is {@code null} on a
 *     user the core builds before its first save, and set on every user the repository returns.
 * @param updatedAt when the user was last saved. An audit field like {@code createdAt}.
 * @param version the optimistic lock. Owned by persistence: {@code null} on a user the core builds before its first
 *     save, set on every user the repository returns, and carried along unchanged by the core. A save of a user
 *     whose version differs from the stored one fails.
 */
public record User(
        UserId id,
        Username username,
        PasswordHash passwordHash,
        boolean enabled,
        boolean mustChangePassword,
        int failedLoginCount,
        Optional<Instant> lockedUntil,
        Instant createdAt,
        Instant updatedAt,
        Set<RoleId> roleIds,
        Long version) {

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(lockedUntil, "lockedUntil");
        roleIds = Set.copyOf(roleIds);
    }

    /** The same user with another set of roles. */
    public User withRoleIds(Set<RoleId> newRoleIds) {
        return new User(
                id,
                username,
                passwordHash,
                enabled,
                mustChangePassword,
                failedLoginCount,
                lockedUntil,
                createdAt,
                updatedAt,
                newRoleIds,
                version);
    }
}
