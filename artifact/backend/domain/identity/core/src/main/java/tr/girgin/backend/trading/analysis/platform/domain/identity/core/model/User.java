package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A person who signs in, with the roles assigned to them.
 *
 * @param lastLoginAt when the user last logged in successfully; empty until then. A domain fact: the core sets it
 *     from its clock.
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
        Optional<Instant> lastLoginAt,
        Instant createdAt,
        Instant updatedAt,
        Set<RoleId> roleIds,
        Long version) {

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(lockedUntil, "lockedUntil");
        Objects.requireNonNull(lastLoginAt, "lastLoginAt");
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
                lastLoginAt,
                createdAt,
                updatedAt,
                newRoleIds,
                version);
    }

    /** The same user, enabled or disabled. */
    public User withEnabled(boolean newEnabled) {
        return new User(
                id,
                username,
                passwordHash,
                newEnabled,
                mustChangePassword,
                failedLoginCount,
                lockedUntil,
                lastLoginAt,
                createdAt,
                updatedAt,
                roleIds,
                version);
    }

    /** The same user with another password hash and change-password flag. */
    public User withPassword(PasswordHash newPasswordHash, boolean newMustChangePassword) {
        return new User(
                id,
                username,
                newPasswordHash,
                enabled,
                newMustChangePassword,
                failedLoginCount,
                lockedUntil,
                lastLoginAt,
                createdAt,
                updatedAt,
                roleIds,
                version);
    }

    /** The same user with another count of failed logins and lock. */
    public User withFailedLogin(int newFailedLoginCount, Optional<Instant> newLockedUntil) {
        return new User(
                id,
                username,
                passwordHash,
                enabled,
                mustChangePassword,
                newFailedLoginCount,
                newLockedUntil,
                lastLoginAt,
                createdAt,
                updatedAt,
                roleIds,
                version);
    }

    /** The same user with no failed logins and no lock. */
    public User withLoginSucceeded() {
        return withFailedLogin(0, Optional.empty());
    }

    /** The same user, last logged in at the given time. */
    public User withLastLogin(Instant at) {
        return new User(
                id,
                username,
                passwordHash,
                enabled,
                mustChangePassword,
                failedLoginCount,
                lockedUntil,
                Optional.of(at),
                createdAt,
                updatedAt,
                roleIds,
                version);
    }

    /** Whether the account is locked at the given time: a lock is in force until its instant is reached. */
    public boolean isLockedAt(Instant now) {
        return lockedUntil.filter(now::isBefore).isPresent();
    }
}
