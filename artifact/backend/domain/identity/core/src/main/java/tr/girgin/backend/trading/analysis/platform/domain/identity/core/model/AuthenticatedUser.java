package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

import java.util.Objects;
import java.util.Set;

/**
 * A user who passed authentication, as they are now.
 *
 * @param roleNames the names of the user's roles
 * @param permissions the union of the permissions of those roles
 * @param mustChangePassword whether the user has to set a new password before anything else
 */
public record AuthenticatedUser(
        UserId id, Username username, Set<String> roleNames, Set<Permission> permissions, boolean mustChangePassword) {

    public AuthenticatedUser {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(username, "username");
        roleNames = Set.copyOf(roleNames);
        permissions = Set.copyOf(permissions);
    }
}
