package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

import java.util.Objects;
import java.util.Set;

/**
 * A named set of permissions. Built-in roles are the ones the system ships with.
 *
 * @param version the optimistic lock. Owned by persistence: {@code null} on a role the core builds before its
 *     first save, set on every role the repository returns, and carried along unchanged by the core. A save of a
 *     role whose version differs from the stored one fails.
 */
public record Role(
        RoleId id, String name, boolean builtIn, String description, Set<Permission> permissions, Long version) {

    public Role {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        permissions = Set.copyOf(permissions);
    }

    /** The same role with another set of permissions. */
    public Role withPermissions(Set<Permission> newPermissions) {
        return new Role(id, name, builtIn, description, newPermissions, version);
    }
}
