package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

final class FakeUserStore implements UserRepositoryPort {

    private final List<User> users = new ArrayList<>();
    private final List<User> saved = new ArrayList<>();
    private int failSaves;
    private final Instant now;

    FakeUserStore(Instant now) {
        this.now = now;
    }

    List<User> users() {
        return users;
    }

    List<User> saved() {
        return saved;
    }

    void failNextSaves(int count) {
        failSaves = count;
    }

    /** Puts a user into the store without counting it as a save. */
    User add(User user) {
        users.add(user);
        return user;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return users.stream().filter(u -> u.id().equals(id)).findFirst();
    }

    @Override
    public Optional<User> findByUsername(Username username) {
        return users.stream()
                .filter(u -> u.username().value().equalsIgnoreCase(username.value()))
                .findFirst();
    }

    @Override
    public List<User> findAll() {
        return users.stream()
                .sorted(Comparator.comparing(u -> u.username().value().toLowerCase()))
                .toList();
    }

    @Override
    public long count() {
        return users.size();
    }

    @Override
    public long countEnabledWithRole(RoleId role) {
        return users.stream()
                .filter(u -> u.enabled() && u.roleIds().contains(role))
                .count();
    }

    @Override
    public long countWithRole(RoleId role) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<RoleId, Long> countUsersByRole() {
        throw new UnsupportedOperationException();
    }

    @Override
    public User save(User user) {
        if (failSaves > 0) {
            failSaves--;
            throw new OptimisticLockingFailureException("stale");
        }
        if (user.version() == null && findByUsername(user.username()).isPresent()) {
            throw new DuplicateKeyException("duplicate");
        }
        User stored = user.version() == null
                ? new User(
                        user.id(),
                        user.username(),
                        user.passwordHash(),
                        user.enabled(),
                        user.mustChangePassword(),
                        user.failedLoginCount(),
                        user.lockedUntil(),
                        user.lastLoginAt(),
                        now,
                        now,
                        user.roleIds(),
                        1L)
                : user;
        saved.add(stored);
        users.removeIf(u -> u.id().equals(stored.id()));
        users.add(stored);
        return stored;
    }
}
