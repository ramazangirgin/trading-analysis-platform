package tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;

/**
 * A user as an admin sees them: no password hash, the roles by name, and the lock as of now.
 *
 * @param roles the user's roles, sorted by name
 * @param locked whether the account is locked now; an expired lock reads as not locked
 * @param lockedUntil when the lock ends, empty when there is none
 * @param lastLoginAt when the user last logged in successfully, empty until then
 * @param createdAt when the user was created
 * @param updatedAt when the user was last changed
 * @param version the optimistic lock, to hand back with an update
 */
public record UserAccount(
        UserId id,
        Username username,
        List<Role> roles,
        boolean enabled,
        boolean mustChangePassword,
        boolean locked,
        Optional<Instant> lockedUntil,
        Optional<Instant> lastLoginAt,
        Instant createdAt,
        Instant updatedAt,
        Long version) {}
