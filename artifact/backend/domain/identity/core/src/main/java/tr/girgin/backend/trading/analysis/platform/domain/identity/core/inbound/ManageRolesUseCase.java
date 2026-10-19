package tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;

/**
 * What admins do with roles. The built-in roles cannot be renamed or deleted, and the permissions of ADMIN cannot
 * change, so the platform always has a role with every permission.
 */
public interface ManageRolesUseCase {

    /** All roles, sorted by name. */
    List<RoleSummary> list();

    /**
     * Finds one role.
     *
     * @throws tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException
     *     {@code ROLE_NOT_FOUND}
     */
    RoleSummary get(RoleId id);

    /**
     * Creates a custom role. The name is unique ignoring case.
     *
     * @throws tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException
     *     {@code INVALID_ROLE_NAME}, {@code INVALID_ROLE_DESCRIPTION} or {@code ROLE_NAME_TAKEN}
     */
    RoleSummary create(String name, String description, Set<Permission> permissions);

    /**
     * Changes a role; an empty argument leaves its part unchanged, and an update that changes nothing writes
     * nothing. A built-in role keeps its name and description, and ADMIN keeps its permissions; sending the
     * current value is not a change.
     *
     * @param expectedVersion the version the caller read; {@code null} to apply the change to what is stored
     * @throws tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException
     *     {@code ROLE_NOT_FOUND}, {@code INVALID_ROLE_NAME}, {@code INVALID_ROLE_DESCRIPTION},
     *     {@code ROLE_NAME_TAKEN}, {@code BUILT_IN_ROLE} or {@code CONCURRENT_UPDATE}
     */
    RoleSummary update(
            RoleId id,
            Optional<String> name,
            Optional<String> description,
            Optional<Set<Permission>> permissions,
            Long expectedVersion);

    /**
     * Deletes a custom role that no user holds, enabled or not.
     *
     * @throws tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException
     *     {@code ROLE_NOT_FOUND}, {@code BUILT_IN_ROLE} or {@code ROLE_IN_USE}
     */
    void delete(RoleId id);
}
