package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.AuthenticateUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.AuthenticatedUser;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.RoleRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

@Service
class AuthenticationService implements AuthenticateUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserRepositoryPort users;
    private final RoleRepositoryPort roles;
    private final PasswordHasherPort hasher;
    /** Checked when the user is unknown, so that case takes as long as a wrong password. */
    private final PasswordHash decoyHash;

    AuthenticationService(UserRepositoryPort users, RoleRepositoryPort roles, PasswordHasherPort hasher) {
        this.users = users;
        this.roles = roles;
        this.hasher = hasher;
        this.decoyHash = hasher.hash(UUID.randomUUID().toString());
    }

    @Override
    public AuthenticatedUser authenticate(String username, CharSequence password) {
        Optional<User> found = findByUsername(username);
        if (found.isEmpty()) {
            hasher.matches(password, decoyHash);
            throw badCredentials();
        }
        User user = found.get();
        if (!hasher.matches(password, user.passwordHash()) || !user.enabled()) {
            throw badCredentials();
        }
        User current = user;
        if (hasher.needsRehash(user.passwordHash())) {
            current = rehash(user, password);
        }
        return toAuthenticated(current);
    }

    @Override
    public Optional<AuthenticatedUser> findActiveUser(UserId id) {
        return users.findById(id).filter(User::enabled).map(this::toAuthenticated);
    }

    private Optional<User> findByUsername(String username) {
        Username name;
        try {
            name = new Username(username);
        } catch (IdentityException _) {
            return Optional.empty();
        }
        return users.findByUsername(name);
    }

    private User rehash(User user, CharSequence password) {
        User updated = new User(
                user.id(),
                user.username(),
                hasher.hash(password),
                user.enabled(),
                user.mustChangePassword(),
                user.failedLoginCount(),
                user.lockedUntil(),
                user.createdAt(),
                user.updatedAt(),
                user.roleIds(),
                user.version());
        try {
            return users.save(updated);
        } catch (OptimisticLockingFailureException e) {
            log.debug(
                    "Password rehash skipped, user {} was updated concurrently",
                    user.id().value(),
                    e);
            return user;
        }
    }

    private AuthenticatedUser toAuthenticated(User user) {
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

    private static IdentityException badCredentials() {
        return new IdentityException(IdentityError.BAD_CREDENTIALS, "Bad credentials", Map.of());
    }
}
