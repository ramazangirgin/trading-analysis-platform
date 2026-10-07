package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** A person who signs in, with the roles assigned to them. */
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
        Set<RoleId> roleIds) {

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(lockedUntil, "lockedUntil");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
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
                newRoleIds);
    }
}
