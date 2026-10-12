package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.AuthenticatedUser;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.RoleRepositoryPort;

/** Builds the {@link AuthenticatedUser} of a stored user: the role names and the union of the permissions. */
@Component
class AuthenticatedUserFactory {

    private final RoleRepositoryPort roles;

    AuthenticatedUserFactory(RoleRepositoryPort roles) {
        this.roles = roles;
    }

    AuthenticatedUser create(User user) {
        Set<String> roleNames = new HashSet<>();
        Set<Permission> permissions = new HashSet<>();
        user.roleIds()
                .forEach(roleId -> roles.findById(roleId).ifPresent(role -> addRole(role, roleNames, permissions)));
        return new AuthenticatedUser(user.id(), user.username(), roleNames, permissions, user.mustChangePassword());
    }

    private static void addRole(Role role, Set<String> roleNames, Set<Permission> permissions) {
        roleNames.add(role.name());
        permissions.addAll(role.permissions());
    }
}
