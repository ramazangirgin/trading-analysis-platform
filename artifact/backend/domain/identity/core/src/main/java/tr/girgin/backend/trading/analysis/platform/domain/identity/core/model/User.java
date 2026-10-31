package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

import java.time.Instant;
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
        Long version) {}
