package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
    private final AuthenticatedUserService authenticatedUsers;
    private final PasswordHasherPort hasher;
    /** Checked when the user is unknown, so that case takes as long as a wrong password. */
    private final PasswordHash decoyHash;

    private final Clock clock;
    private final LoginLockoutService lockoutPolicy;
    private final UserChangeService userChanges;
    private final UsernameService usernames;

    AuthenticationService(
            UserRepositoryPort users,
            AuthenticatedUserService authenticatedUsers,
            PasswordHasherPort hasher,
            Clock clock,
            LoginLockoutService lockoutPolicy,
            UserChangeService userChanges,
            UsernameService usernames) {
        this.users = users;
        this.authenticatedUsers = authenticatedUsers;
        this.hasher = hasher;
        this.clock = clock;
        this.lockoutPolicy = lockoutPolicy;
        this.userChanges = userChanges;
        this.usernames = usernames;
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
        if (lockoutPolicy.isLocked(user, clock.instant())) {
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
            name = usernames.username(username);
        } catch (IdentityException _) {
            return Optional.empty();
        }
        return users.findByUsername(name);
    }

    /** Counts a wrong password and locks the account when the count reaches the limit. Never changes the answer. */
    private void countFailure(User user) {
        AtomicReference<Instant> lockSet = new AtomicReference<>();
        Optional<User> saved = saveWithRetry(user, current -> withFailure(current, lockSet));
        if (saved.isEmpty()) {
            log.warn(
                    "Failed login of user {} not counted, the user was updated concurrently",
                    user.id().value());
            return;
        }
        Instant until = lockSet.get();
        if (until != null) {
            log.info(
                    "User {} locked until {} after {} failed logins",
                    user.id().value(),
                    until,
                    lockoutPolicy.maxFailedAttempts());
        }
    }

    /** Applies one failure; {@code lockSet} holds the lock this step set, or null when it set none. */
    private User withFailure(User user, AtomicReference<Instant> lockSet) {
        lockSet.set(null);
        Instant now = clock.instant();
        if (lockoutPolicy.isLocked(user, now)) {
            return user;
        }
        int count = user.failedLoginCount() + 1;
        if (count >= lockoutPolicy.maxFailedAttempts()) {
            Instant until = now.plus(lockoutPolicy.lockoutDuration());
            lockSet.set(until);
            return userChanges.withFailedLogin(user, 0, Optional.of(until));
        }
        return userChanges.withFailedLogin(user, count, Optional.empty());
    }

    /**
     * Sets the time of the login, clears the count and lock, and stores an outdated hash again, in one save.
     * Fails the login when the user was disabled or the password no longer matches while it was checked. When
     * nothing could be saved, the user is loaded once more and must pass the same check.
     */
    private User recordSuccess(User user, CharSequence password) {
        PasswordHash checked = user.passwordHash();
        return saveWithRetry(user, current -> {
                    requireStillValid(current, checked, password);
                    User updated = userChanges.withLastLogin(current, clock.instant());
                    if (current.failedLoginCount() > 0 || current.lockedUntil().isPresent()) {
                        updated = lockoutPolicy.unlocked(updated);
                    }
                    if (hasher.needsRehash(current.passwordHash())) {
                        updated =
                                userChanges.withPassword(updated, hasher.hash(password), current.mustChangePassword());
                    }
                    return updated;
                })
                .orElseGet(() -> {
                    User latest = users.findById(user.id()).orElseThrow(this::badCredentials);
                    requireStillValid(latest, checked, password);
                    return latest;
                });
    }

    /** A different hash is only paid for with a BCrypt check: a rehash keeps the password, a reset does not. */
    private void requireStillValid(User current, PasswordHash checked, CharSequence password) {
        if (!current.enabled()) {
            throw badCredentials();
        }
        if (!current.passwordHash().equals(checked) && !hasher.matches(password, current.passwordHash())) {
            throw badCredentials();
        }
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

    private IdentityException badCredentials() {
        return new IdentityException(IdentityError.BAD_CREDENTIALS, "Bad credentials", Map.of());
    }
}
