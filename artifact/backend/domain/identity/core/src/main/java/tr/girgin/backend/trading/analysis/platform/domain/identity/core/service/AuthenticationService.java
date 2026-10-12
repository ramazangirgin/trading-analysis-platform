package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.util.Map;
import java.util.Optional;
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
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

@Service
class AuthenticationService implements AuthenticateUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserRepositoryPort users;
    private final AuthenticatedUserFactory authenticatedUsers;
    private final PasswordHasherPort hasher;
    /** Checked when the user is unknown, so that case takes as long as a wrong password. */
    private final PasswordHash decoyHash;

    AuthenticationService(
            UserRepositoryPort users, AuthenticatedUserFactory authenticatedUsers, PasswordHasherPort hasher) {
        this.users = users;
        this.authenticatedUsers = authenticatedUsers;
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
        return authenticatedUsers.create(current);
    }

    @Override
    public Optional<AuthenticatedUser> findActiveUser(UserId id) {
        return users.findById(id).filter(User::enabled).map(authenticatedUsers::create);
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
        User updated = user.withPassword(hasher.hash(password), user.mustChangePassword());
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

    private static IdentityException badCredentials() {
        return new IdentityException(IdentityError.BAD_CREDENTIALS, "Bad credentials", Map.of());
    }
}
