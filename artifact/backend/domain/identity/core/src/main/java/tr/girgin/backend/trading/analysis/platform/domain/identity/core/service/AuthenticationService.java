package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
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
    private static final int MAX_ATTEMPTS = 3;

    private final UserRepositoryPort users;
    private final AuthenticatedUserFactory authenticatedUsers;
    private final PasswordHasherPort hasher;
    /** Checked when the user is unknown, so that case takes as long as a wrong password. */
    private final PasswordHash decoyHash;

    private final Clock clock;
    private final LoginLockoutPolicy lockoutPolicy;

    AuthenticationService(
            UserRepositoryPort users,
            AuthenticatedUserFactory authenticatedUsers,
            PasswordHasherPort hasher,
            Clock clock,
            LoginLockoutPolicy lockoutPolicy) {
        this.users = users;
        this.authenticatedUsers = authenticatedUsers;
        this.hasher = hasher;
        this.clock = clock;
        this.lockoutPolicy = lockoutPolicy;
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
        boolean matches = hasher.matches(password, user.passwordHash());
        if (user.isLockedAt(clock.instant())) {
            throw badCredentials();
        }
        if (!matches) {
            countFailure(user);
            throw badCredentials();
        }
        if (!user.enabled()) {
            throw badCredentials();
        }
        return authenticatedUsers.create(recordSuccess(user, password));
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

    /** Counts a wrong password and locks the account when the count reaches the limit. Never changes the answer. */
    private void countFailure(User user) {
        Optional<User> saved = saveWithRetry(user, this::withFailure);
        if (saved.isEmpty()) {
            log.warn(
                    "Failed login of user {} not counted, the user was updated concurrently",
                    user.id().value());
            return;
        }
        saved.get()
                .lockedUntil()
                .ifPresent(until -> log.info(
                        "User {} locked until {} after {} failed logins",
                        user.id().value(),
                        until,
                        lockoutPolicy.maxFailedAttempts()));
    }

    private User withFailure(User user) {
        Instant now = clock.instant();
        if (user.isLockedAt(now)) {
            return user;
        }
        int count = user.failedLoginCount() + 1;
        if (count >= lockoutPolicy.maxFailedAttempts()) {
            return user.withFailedLogin(0, Optional.of(now.plus(lockoutPolicy.lockoutDuration())));
        }
        return user.withFailedLogin(count, Optional.empty());
    }

    /** Clears the count and lock, and stores an outdated hash again, in one save. Never fails the login. */
    private User recordSuccess(User user, CharSequence password) {
        PasswordHash checked = user.passwordHash();
        return saveWithRetry(user, current -> {
                    User updated = current;
                    if (current.failedLoginCount() > 0 || current.lockedUntil().isPresent()) {
                        updated = updated.withLoginSucceeded();
                    }
                    if (current.passwordHash().equals(checked) && hasher.needsRehash(checked)) {
                        updated = updated.withPassword(hasher.hash(password), current.mustChangePassword());
                    }
                    return updated;
                })
                .orElse(user);
    }

    /**
     * Saves what {@code step} makes of the user; a step that returns the same instance writes nothing. On an
     * optimistic-lock conflict the user is loaded again and the step applied again, up to 3 times in all.
     *
     * @return the saved user, or empty when every attempt conflicted or the user is gone
     */
    private Optional<User> saveWithRetry(User user, UnaryOperator<User> step) {
        User current = user;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            User next = step.apply(current);
            if (next == current) {
                return Optional.of(current);
            }
            try {
                return Optional.of(users.save(next));
            } catch (OptimisticLockingFailureException e) {
                log.debug(
                        "User {} was updated concurrently, attempt {}",
                        user.id().value(),
                        attempt,
                        e);
                if (attempt < MAX_ATTEMPTS) {
                    Optional<User> reloaded = users.findById(user.id());
                    if (reloaded.isEmpty()) {
                        return Optional.empty();
                    }
                    current = reloaded.get();
                }
            }
        }
        return Optional.empty();
    }

    private static IdentityException badCredentials() {
        return new IdentityException(IdentityError.BAD_CREDENTIALS, "Bad credentials", Map.of());
    }
}
