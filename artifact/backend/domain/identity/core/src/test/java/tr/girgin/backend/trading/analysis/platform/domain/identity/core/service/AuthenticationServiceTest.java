package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.AuthenticatedUser;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.RoleRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

class AuthenticationServiceTest {

    private final FakeUserRepository users = new FakeUserRepository();
    private final FakeRoleRepository roles = new FakeRoleRepository();
    private final FakeHasher hasher = new FakeHasher();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T10:00:00Z"));
    private final AuthenticationService service = new AuthenticationService(
            users,
            new AuthenticatedUserFactory(roles),
            hasher,
            clock,
            new LoginLockoutPolicy(5, Duration.ofMinutes(15)));

    @Test
    void validLoginReturnsTheUserWithRoleNamesAndTheUnionOfPermissions() {
        Role reader = role("reader", Permission.ANALYSIS_READ, Permission.PRESET_READ);
        Role runner = role("runner", Permission.ANALYSIS_READ, Permission.ANALYSIS_RUN);
        User user = user("alice", "secret", true, reader.id(), runner.id());
        roles.roles.put(reader.id(), reader);
        roles.roles.put(runner.id(), runner);
        users.users.add(user);

        AuthenticatedUser result = service.authenticate("alice", "secret");

        assertThat(result.id()).isEqualTo(user.id());
        assertThat(result.username()).isEqualTo(user.username());
        assertThat(result.roleNames()).containsExactlyInAnyOrder("reader", "runner");
        assertThat(result.permissions())
                .containsExactlyInAnyOrder(Permission.ANALYSIS_READ, Permission.PRESET_READ, Permission.ANALYSIS_RUN);
        assertThat(result.mustChangePassword()).isFalse();
    }

    @Test
    void mustChangePasswordIsCopiedIntoTheResult() {
        User user = user("alice", "secret", true);
        users.users.add(new User(
                user.id(),
                user.username(),
                user.passwordHash(),
                true,
                true,
                0,
                Optional.empty(),
                Optional.empty(),
                null,
                null,
                Set.of(),
                1L));

        assertThat(service.authenticate("alice", "secret").mustChangePassword()).isTrue();
    }

    @Test
    void everyFailureIsTheSameBadCredentialsError() {
        users.users.add(user("alice", "secret", true));
        users.users.add(user("bob", "secret", false));

        List<IdentityException> errors = List.of(
                failure("alice", "wrong"),
                failure("nobody", "secret"),
                failure("x", "secret"),
                failure("bob", "secret"));

        assertThat(errors).allSatisfy(e -> {
            assertThat(e.error()).isEqualTo(IdentityError.BAD_CREDENTIALS);
            assertThat(e.params()).isEmpty();
            assertThat(e.getMessage()).isEqualTo(errors.getFirst().getMessage());
        });
    }

    @Test
    void anUnknownUserStillCostsOnePasswordCheck() {
        hasher.matchCalls = 0;

        failure("nobody", "secret");
        assertThat(hasher.matchCalls).isEqualTo(1);

        failure("x", "secret");
        assertThat(hasher.matchCalls).isEqualTo(2);
    }

    @Test
    void anOutdatedHashIsReplacedOnceOnLogin() {
        users.users.add(user("alice", "secret", true));
        hasher.rehashNeeded = true;

        service.authenticate("alice", "secret");

        assertThat(users.saved).hasSize(1);
        assertThat(users.saved.getFirst().passwordHash()).isEqualTo(new PasswordHash("new:secret"));
        assertThat(service.authenticate("alice", "secret").username().value()).isEqualTo("alice");
    }

    @Test
    void aCurrentHashIsNotSavedAgain() {
        users.users.add(user("alice", "secret", true));

        service.authenticate("alice", "secret");

        assertThat(users.saved).allSatisfy(u -> assertThat(u.passwordHash()).isEqualTo(new PasswordHash("old:secret")));
    }

    @Test
    void aConcurrentUpdateDuringRehashStillLogsTheUserIn() {
        users.users.add(user("alice", "secret", true));
        hasher.rehashNeeded = true;
        users.failSaves = Integer.MAX_VALUE;

        assertThat(service.authenticate("alice", "secret").username().value()).isEqualTo("alice");
    }

    @Test
    void theCountRisesWithEachWrongPassword() {
        User alice = user("alice", "secret", true);
        users.users.add(alice);

        failure("alice", "wrong");
        assertThat(stored(alice).failedLoginCount()).isEqualTo(1);
        failure("alice", "wrong");
        failure("alice", "wrong");

        assertThat(stored(alice).failedLoginCount()).isEqualTo(3);
        assertThat(stored(alice).lockedUntil()).isEmpty();
    }

    @Test
    void theFifthWrongPasswordLocksForFifteenMinutesAndResetsTheCount() {
        User alice = user("alice", "secret", true);
        users.users.add(alice);

        for (int i = 0; i < 5; i++) {
            failure("alice", "wrong");
        }

        assertThat(stored(alice).failedLoginCount()).isZero();
        assertThat(stored(alice).lockedUntil()).contains(clock.instant().plus(Duration.ofMinutes(15)));
    }

    @Test
    void aLockedAccountFailsLikeAWrongPasswordEvenWithTheRightOneAndIsNotCounted() {
        User alice = user("alice", "secret", true);
        users.users.add(alice);
        IdentityException wrong = failure("alice", "wrong");
        for (int i = 0; i < 4; i++) {
            failure("alice", "wrong");
        }
        User locked = stored(alice);
        users.saved.clear();

        IdentityException right = failure("alice", "secret");
        failure("alice", "wrong");

        assertThat(right.error()).isEqualTo(IdentityError.BAD_CREDENTIALS);
        assertThat(right.params()).isEmpty();
        assertThat(right.getMessage()).isEqualTo(wrong.getMessage());
        assertThat(users.saved).isEmpty();
        assertThat(stored(alice)).isEqualTo(locked);
    }

    @Test
    void afterTheLockExpiresTheRightPasswordSucceedsAndClearsCountAndLock() {
        User alice = user("alice", "secret", true);
        users.users.add(alice);
        for (int i = 0; i < 5; i++) {
            failure("alice", "wrong");
        }

        clock.advance(Duration.ofMinutes(15));
        service.authenticate("alice", "secret");

        assertThat(stored(alice).failedLoginCount()).isZero();
        assertThat(stored(alice).lockedUntil()).isEmpty();
    }

    @Test
    void aWrongPasswordAfterTheLockExpiredStartsANewCount() {
        User alice = user("alice", "secret", true);
        users.users.add(alice);
        for (int i = 0; i < 5; i++) {
            failure("alice", "wrong");
        }
        clock.advance(Duration.ofMinutes(15));

        failure("alice", "wrong");

        assertThat(stored(alice).failedLoginCount()).isEqualTo(1);
        assertThat(stored(alice).lockedUntil()).isEmpty();
    }

    @Test
    void aSuccessAfterThreeFailuresResetsTheCount() {
        User alice = user("alice", "secret", true);
        users.users.add(alice);
        for (int i = 0; i < 3; i++) {
            failure("alice", "wrong");
        }

        service.authenticate("alice", "secret");

        assertThat(stored(alice).failedLoginCount()).isZero();
    }

    @Test
    void aNormalLoginOnlyRecordsTheTimeOfTheLogin() {
        User alice = user("alice", "secret", true);
        users.users.add(alice);

        service.authenticate("alice", "secret");

        assertThat(users.saved).hasSize(1);
        assertThat(users.saved.getFirst()).isEqualTo(alice.withLastLogin(clock.instant()));
    }

    @Test
    void aSuccessfulLoginSetsTheLastLoginToTheTimeOfTheClock() {
        User alice = user("alice", "secret", true);
        users.users.add(alice);
        assertThat(alice.lastLoginAt()).isEmpty();

        service.authenticate("alice", "secret");
        assertThat(stored(alice).lastLoginAt()).contains(Instant.parse("2026-10-01T10:00:00Z"));

        clock.advance(Duration.ofHours(2));
        service.authenticate("alice", "secret");
        assertThat(stored(alice).lastLoginAt()).contains(Instant.parse("2026-10-01T12:00:00Z"));
    }

    @Test
    void aFailedLoginDoesNotSetTheLastLogin() {
        User alice = user("alice", "secret", true);
        users.users.add(alice);

        failure("alice", "wrong");
        users.users.add(user("bob", "secret", false));
        failure("bob", "secret");

        assertThat(stored(alice).lastLoginAt()).isEmpty();
        assertThat(users.users)
                .filteredOn(u -> u.username().value().equals("bob"))
                .allSatisfy(u -> assertThat(u.lastLoginAt()).isEmpty());
    }

    @Test
    void rehashAndResetGoIntoOneSave() {
        User alice = user("alice", "secret", true);
        users.users.add(alice);
        failure("alice", "wrong");
        users.saved.clear();
        hasher.rehashNeeded = true;

        service.authenticate("alice", "secret");

        assertThat(users.saved).hasSize(1);
        assertThat(users.saved.getFirst().passwordHash()).isEqualTo(new PasswordHash("new:secret"));
        assertThat(users.saved.getFirst().failedLoginCount()).isZero();
    }

    @Test
    void anUnknownUserIsNotCounted() {
        failure("nobody", "secret");

        assertThat(users.saved).isEmpty();
    }

    @Test
    void aDisabledUserWithTheRightPasswordIsNotCounted() {
        users.users.add(user("bob", "secret", false));

        failure("bob", "secret");

        assertThat(users.saved).isEmpty();
    }

    @Test
    void anOptimisticLockConflictIsRetried() {
        User alice = user("alice", "secret", true);
        users.users.add(alice);
        users.failSaves = 2;

        IdentityException e = failure("alice", "wrong");

        assertThat(e.error()).isEqualTo(IdentityError.BAD_CREDENTIALS);
        assertThat(stored(alice).failedLoginCount()).isEqualTo(1);
    }

    @Test
    void afterThreeConflictsTheAnswerIsStillBadCredentialsAndNothingIsCounted() {
        User alice = user("alice", "secret", true);
        users.users.add(alice);
        users.failSaves = 3;

        IdentityException e = failure("alice", "wrong");

        assertThat(e.error()).isEqualTo(IdentityError.BAD_CREDENTIALS);
        assertThat(e.params()).isEmpty();
        assertThat(stored(alice).failedLoginCount()).isZero();
    }

    @Test
    void aConflictOnTheResetStillLogsTheUserIn() {
        User alice = user("alice", "secret", true);
        users.users.add(alice);
        failure("alice", "wrong");
        users.failSaves = 3;

        assertThat(service.authenticate("alice", "secret").id()).isEqualTo(alice.id());
    }

    private User stored(User user) {
        return users.findById(user.id()).orElseThrow();
    }

    @Test
    void findActiveUserIsEmptyForAMissingOrDisabledUser() {
        User disabled = user("bob", "secret", false);
        users.users.add(disabled);

        assertThat(service.findActiveUser(UserId.newId())).isEmpty();
        assertThat(service.findActiveUser(disabled.id())).isEmpty();
    }

    @Test
    void findActiveUserReturnsAnEnabledUser() {
        User user = user("alice", "secret", true);
        users.users.add(user);

        assertThat(service.findActiveUser(user.id()))
                .hasValueSatisfying(u -> assertThat(u.id()).isEqualTo(user.id()));
    }

    private IdentityException failure(String username, String password) {
        IdentityException[] caught = new IdentityException[1];
        assertThatThrownBy(() -> service.authenticate(username, password))
                .isInstanceOfSatisfying(IdentityException.class, e -> caught[0] = e);
        return caught[0];
    }

    private static Role role(String name, Permission... permissions) {
        return new Role(RoleId.newId(), name, false, name, Set.of(permissions), 1L);
    }

    private static User user(String name, String password, boolean enabled, RoleId... roleIds) {
        return new User(
                UserId.newId(),
                new Username(name),
                new PasswordHash("old:" + password),
                enabled,
                false,
                0,
                Optional.empty(),
                Optional.empty(),
                Instant.parse("2026-10-01T10:00:00Z"),
                Instant.parse("2026-10-01T10:00:00Z"),
                Set.of(roleIds),
                1L);
    }

    private static final class FakeHasher implements PasswordHasherPort {

        private int matchCalls;
        private boolean rehashNeeded;

        @Override
        public PasswordHash hash(CharSequence rawPassword) {
            return new PasswordHash("new:" + rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, PasswordHash hash) {
            matchCalls++;
            return hash.value().endsWith(":" + rawPassword);
        }

        @Override
        public boolean needsRehash(PasswordHash hash) {
            return rehashNeeded && hash.value().startsWith("old:");
        }
    }

    private static final class FakeUserRepository implements UserRepositoryPort {

        private final List<User> users = new ArrayList<>();
        private final List<User> saved = new ArrayList<>();
        private int failSaves;

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
            return List.copyOf(users);
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
        public User save(User user) {
            if (failSaves > 0) {
                failSaves--;
                throw new OptimisticLockingFailureException("stale");
            }
            saved.add(user);
            users.removeIf(u -> u.id().equals(user.id()));
            users.add(user);
            return user;
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class FakeRoleRepository implements RoleRepositoryPort {

        private final Map<RoleId, Role> roles = new HashMap<>();

        @Override
        public Optional<Role> findById(RoleId id) {
            return Optional.ofNullable(roles.get(id));
        }

        @Override
        public Optional<Role> findByName(String name) {
            return roles.values().stream().filter(r -> r.name().equals(name)).findFirst();
        }

        @Override
        public List<Role> findAll() {
            return List.copyOf(roles.values());
        }

        @Override
        public Role save(Role role) {
            roles.put(role.id(), role);
            return role;
        }
    }
}
