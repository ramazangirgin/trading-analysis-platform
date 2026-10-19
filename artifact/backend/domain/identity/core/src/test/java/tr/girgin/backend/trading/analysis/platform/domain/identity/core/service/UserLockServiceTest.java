package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

class UserLockServiceTest {

    private final FakeUserRepository users = new FakeUserRepository();
    private final UserLockService service = new UserLockService(users);

    @Test
    void unlockClearsTheLockAndTheCount() {
        User locked = user(3, Optional.of(Instant.parse("2026-10-01T10:15:00Z")));
        users.user = locked;

        service.unlock(locked.id());

        assertThat(users.user.failedLoginCount()).isZero();
        assertThat(users.user.lockedUntil()).isEmpty();
        assertThat(users.saved).hasSize(1);
    }

    @Test
    void anUnlockedUserWithNoFailuresIsNotWritten() {
        User free = user(0, Optional.empty());
        users.user = free;

        service.unlock(free.id());

        assertThat(users.saved).isEmpty();
    }

    @Test
    void anUnknownIdIsUserNotFound() {
        UserId id = UserId.newId();

        assertThatThrownBy(() -> service.unlock(id)).isInstanceOfSatisfying(IdentityException.class, e -> {
            assertThat(e.error()).isEqualTo(IdentityError.USER_NOT_FOUND);
            assertThat(e.params()).containsEntry("id", id.value());
        });
    }

    @Test
    void aConflictIsRetried() {
        User locked = user(0, Optional.of(Instant.parse("2026-10-01T10:15:00Z")));
        users.user = locked;
        users.failSaves = 2;

        service.unlock(locked.id());

        assertThat(users.user.lockedUntil()).isEmpty();
    }

    @Test
    void threeConflictsFail() {
        User locked = user(2, Optional.empty());
        users.user = locked;
        users.failSaves = 3;

        assertThatThrownBy(() -> service.unlock(locked.id())).isInstanceOf(OptimisticLockingFailureException.class);
    }

    private static User user(int failedLoginCount, Optional<Instant> lockedUntil) {
        return new User(
                UserId.newId(),
                new Username("alice"),
                new PasswordHash("{noop}x"),
                true,
                false,
                failedLoginCount,
                lockedUntil,
                Optional.empty(),
                Instant.parse("2026-10-01T10:00:00Z"),
                Instant.parse("2026-10-01T10:00:00Z"),
                Set.of(),
                1L);
    }

    private static final class FakeUserRepository implements UserRepositoryPort {

        private User user;
        private final List<User> saved = new ArrayList<>();
        private int failSaves;

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(user).filter(u -> u.id().equals(id));
        }

        @Override
        public Optional<User> findByUsername(Username username) {
            return Optional.ofNullable(user);
        }

        @Override
        public List<User> findAll() {
            return user == null ? List.of() : List.of(user);
        }

        @Override
        public long count() {
            return user == null ? 0 : 1;
        }

        @Override
        public long countEnabledWithRole(RoleId role) {
            return user != null && user.enabled() && user.roleIds().contains(role) ? 1 : 0;
        }

        @Override
        public User save(User toSave) {
            if (failSaves > 0) {
                failSaves--;
                throw new OptimisticLockingFailureException("stale");
            }
            saved.add(toSave);
            user = toSave;
            return toSave;
        }
    }
}
