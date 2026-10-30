package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.CreatedUserAccount;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UserAccount;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

class UserManagementServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    private final FakeUserRepository users = new FakeUserRepository();
    private final InMemoryRoleRepository roles = new InMemoryRoleRepository();
    private final FixedGenerator generator = new FixedGenerator();
    private final UserManagementService service = new UserManagementService(
            users,
            roles,
            new FakeHasher(),
            new PasswordPolicyService(12, "password1234567\n"),
            generator,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new UserChangeService(),
            new UsernameService(),
            new LoginLockoutService(5, Duration.ofMinutes(15)));

    private final Role admin = role(RoleId.ADMIN, "ADMIN", Permission.USER_MANAGE);
    private final Role analyst = role(RoleId.ANALYST, "ANALYST", Permission.ANALYSIS_READ);
    private final Role viewer = role(RoleId.VIEWER, "VIEWER", Permission.ANALYSIS_READ);

    UserManagementServiceTest() {
        List.of(viewer, admin, analyst).forEach(roles::add);
    }

    @Test
    void createWithAnEnteredPasswordStoresItHashedAndFlagged() {
        CreatedUserAccount created =
                service.create("alice", Set.of(RoleId.ANALYST), Optional.of("a-long-enough-password"));

        assertThat(created.generatedPassword()).isEmpty();
        assertThat(created.account().username()).isEqualTo(new Username("alice"));
        assertThat(created.account().enabled()).isTrue();
        assertThat(created.account().mustChangePassword()).isTrue();
        assertThat(created.account().roles()).extracting(Role::name).containsExactly("ANALYST");
        assertThat(created.account().lastLoginAt()).isEmpty();
        User stored = users.findByUsername(new Username("alice")).orElseThrow();
        assertThat(stored.passwordHash()).isEqualTo(new PasswordHash("hash:a-long-enough-password"));
        assertThat(stored.mustChangePassword()).isTrue();
        assertThat(stored.failedLoginCount()).isZero();
        assertThat(stored.lockedUntil()).isEmpty();
    }

    @Test
    void createWithoutAPasswordGeneratesOne() {
        CreatedUserAccount created = service.create("alice", Set.of(), Optional.empty());

        assertThat(created.generatedPassword()).contains("generated-password");
        assertThat(generator.lastLength).isEqualTo(24);
        User stored = users.findByUsername(new Username("alice")).orElseThrow();
        assertThat(stored.passwordHash()).isEqualTo(new PasswordHash("hash:generated-password"));
        assertThat(stored.mustChangePassword()).isTrue();
    }

    @Test
    void aBlankPasswordIsTheSameAsNone() {
        CreatedUserAccount created = service.create("alice", Set.of(), Optional.of("  "));

        assertThat(created.generatedPassword()).isPresent();
    }

    @Test
    void aGeneratedPasswordIsAsLongAsThePolicyAsksForWhenThatIsMore() {
        UserManagementService strict = new UserManagementService(
                users,
                roles,
                new FakeHasher(),
                new PasswordPolicyService(40, "password1234567\n"),
                generator,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new UserChangeService(),
                new UsernameService(),
                new LoginLockoutService(5, Duration.ofMinutes(15)));

        strict.create("alice", Set.of(), Optional.empty());

        assertThat(generator.lastLength).isEqualTo(40);
    }

    @Test
    void createRejectsAWeakEnteredPassword() {
        assertThatThrownBy(() -> service.create("alice", Set.of(), Optional.of("short")))
                .isInstanceOfSatisfying(IdentityException.class, e -> {
                    assertThat(e.error()).isEqualTo(IdentityError.WEAK_PASSWORD);
                    assertThat(e.params()).containsKey("reasons");
                });
        assertThat(users.users).isEmpty();
    }

    @Test
    void createRejectsAnInvalidUsername() {
        assertThatThrownBy(() -> service.create("a b", Set.of(), Optional.empty()))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.INVALID_USERNAME));
    }

    @Test
    void createRejectsAnUnknownRole() {
        RoleId unknown = new RoleId("nope");

        assertThatThrownBy(() -> service.create("alice", Set.of(unknown), Optional.empty()))
                .isInstanceOfSatisfying(IdentityException.class, e -> {
                    assertThat(e.error()).isEqualTo(IdentityError.UNKNOWN_ROLE);
                    assertThat(e.params()).containsEntry("roleId", "nope");
                });
        assertThat(users.users).isEmpty();
    }

    @Test
    void createRejectsATakenUsername() {
        service.create("alice", Set.of(), Optional.empty());

        assertThatThrownBy(() -> service.create("ALICE", Set.of(), Optional.empty()))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.USERNAME_TAKEN));
    }

    @Test
    void updateChangesTheRoles() {
        User target = users.add(user("bob", true, RoleId.VIEWER));

        UserAccount updated = service.update(
                UserId.newId(), target.id(), Optional.of(Set.of(RoleId.ANALYST)), Optional.empty(), null);

        assertThat(updated.roles()).extracting(Role::name).containsExactly("ANALYST");
        assertThat(users.findById(target.id()).orElseThrow().roleIds()).containsExactly(RoleId.ANALYST);
        assertThat(updated.enabled()).isTrue();
    }

    @Test
    void updateDisablesAndEnables() {
        User target = users.add(user("bob", true, RoleId.VIEWER));
        UserId actor = UserId.newId();

        UserAccount disabled = service.update(actor, target.id(), Optional.empty(), Optional.of(false), null);
        assertThat(disabled.enabled()).isFalse();
        assertThat(users.findById(target.id()).orElseThrow().enabled()).isFalse();

        UserAccount enabled = service.update(actor, target.id(), Optional.empty(), Optional.of(true), null);
        assertThat(enabled.enabled()).isTrue();
    }

    @Test
    void anUpdateThatChangesNothingWritesNothing() {
        User target = users.add(user("bob", true, RoleId.VIEWER));

        service.update(UserId.newId(), target.id(), Optional.empty(), Optional.empty(), null);
        service.update(UserId.newId(), target.id(), Optional.of(Set.of(RoleId.VIEWER)), Optional.of(true), null);

        assertThat(users.saved).isEmpty();
    }

    @Test
    void updateRejectsAnUnknownRole() {
        User target = users.add(user("bob", true));

        assertThatThrownBy(() -> service.update(
                        UserId.newId(), target.id(), Optional.of(Set.of(new RoleId("nope"))), Optional.empty(), null))
                .isInstanceOfSatisfying(IdentityException.class, e -> {
                    assertThat(e.error()).isEqualTo(IdentityError.UNKNOWN_ROLE);
                    assertThat(e.params()).containsEntry("roleId", "nope");
                });
    }

    @Test
    void updateOfAnUnknownUserIsUserNotFound() {
        UserId id = UserId.newId();

        assertThatThrownBy(() -> service.update(UserId.newId(), id, Optional.empty(), Optional.of(false), null))
                .isInstanceOfSatisfying(IdentityException.class, e -> {
                    assertThat(e.error()).isEqualTo(IdentityError.USER_NOT_FOUND);
                    assertThat(e.params()).containsEntry("id", id.value());
                });
    }

    @Test
    void aStaleVersionIsAConcurrentUpdate() {
        User target = users.add(user("bob", true, RoleId.VIEWER));

        assertThatThrownBy(() -> service.update(
                        UserId.newId(), target.id(), Optional.empty(), Optional.of(false), target.version() + 1))
                .isInstanceOfSatisfying(IdentityException.class, e -> {
                    assertThat(e.error()).isEqualTo(IdentityError.CONCURRENT_UPDATE);
                    assertThat(e.params()).containsEntry("id", target.id().value());
                });
        assertThat(users.saved).isEmpty();
    }

    @Test
    void theCurrentVersionIsAccepted() {
        User target = users.add(user("bob", true, RoleId.VIEWER));

        UserAccount updated =
                service.update(UserId.newId(), target.id(), Optional.empty(), Optional.of(false), target.version());

        assertThat(updated.enabled()).isFalse();
    }

    @Test
    void aLockingFailureOnSaveBecomesConcurrentUpdate() {
        User target = users.add(user("bob", true, RoleId.VIEWER));
        users.failSaves = 1;

        assertThatThrownBy(
                        () -> service.update(UserId.newId(), target.id(), Optional.empty(), Optional.of(false), null))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.CONCURRENT_UPDATE));
    }

    @Test
    void anAdminCannotDisableThemselves() {
        User self = users.add(user("root", true, RoleId.ADMIN));
        users.add(user("other", true, RoleId.ADMIN));

        assertThatThrownBy(() -> service.update(self.id(), self.id(), Optional.empty(), Optional.of(false), null))
                .isInstanceOfSatisfying(
                        IdentityException.class,
                        e -> assertThat(e.error()).isEqualTo(IdentityError.CANNOT_DISABLE_SELF));
    }

    @Test
    void anAdminCannotRemoveTheirOwnAdminRole() {
        User self = users.add(user("root", true, RoleId.ADMIN));
        users.add(user("other", true, RoleId.ADMIN));

        assertThatThrownBy(() -> service.update(
                        self.id(), self.id(), Optional.of(Set.of(RoleId.VIEWER)), Optional.empty(), null))
                .isInstanceOfSatisfying(
                        IdentityException.class,
                        e -> assertThat(e.error()).isEqualTo(IdentityError.CANNOT_REMOVE_OWN_ADMIN));
    }

    @Test
    void anAdminCanChangeTheirOwnOtherRoles() {
        User self = users.add(user("root", true, RoleId.ADMIN));

        UserAccount updated = service.update(
                self.id(), self.id(), Optional.of(Set.of(RoleId.ADMIN, RoleId.VIEWER)), Optional.empty(), null);

        assertThat(updated.roles()).extracting(Role::name).containsExactly("ADMIN", "VIEWER");
    }

    @Test
    void theLastEnabledAdminCannotBeDisabled() {
        User onlyAdmin = users.add(user("root", true, RoleId.ADMIN));
        users.add(user("off", false, RoleId.ADMIN));
        UserId manager = UserId.newId();

        assertThatThrownBy(() -> service.update(manager, onlyAdmin.id(), Optional.empty(), Optional.of(false), null))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.LAST_ADMIN));
        assertThat(users.findById(onlyAdmin.id()).orElseThrow().enabled()).isTrue();
    }

    @Test
    void theLastEnabledAdminCannotBeDemoted() {
        User onlyAdmin = users.add(user("root", true, RoleId.ADMIN));
        UserId manager = UserId.newId();

        assertThatThrownBy(() -> service.update(
                        manager, onlyAdmin.id(), Optional.of(Set.of(RoleId.VIEWER)), Optional.empty(), null))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.LAST_ADMIN));
    }

    @Test
    void anotherEnabledAdminLetsBothChangesThrough() {
        User first = users.add(user("root", true, RoleId.ADMIN));
        User second = users.add(user("second", true, RoleId.ADMIN));
        UserId manager = UserId.newId();

        UserAccount demoted =
                service.update(manager, first.id(), Optional.of(Set.of(RoleId.VIEWER)), Optional.empty(), null);
        assertThat(demoted.roles()).extracting(Role::name).containsExactly("VIEWER");

        assertThatThrownBy(() -> service.update(manager, second.id(), Optional.empty(), Optional.of(false), null))
                .isInstanceOf(IdentityException.class);
        service.update(manager, first.id(), Optional.of(Set.of(RoleId.ADMIN)), Optional.empty(), null);
        UserAccount disabled = service.update(manager, second.id(), Optional.empty(), Optional.of(false), null);
        assertThat(disabled.enabled()).isFalse();
    }

    @Test
    void aDisabledAdminDoesNotCount() {
        User enabledAdmin = users.add(user("root", true, RoleId.ADMIN));
        users.add(user("off", false, RoleId.ADMIN));

        assertThatThrownBy(() ->
                        service.update(UserId.newId(), enabledAdmin.id(), Optional.empty(), Optional.of(false), null))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.LAST_ADMIN));
    }

    @Test
    void aNonAdminCanBeDisabledWithoutAnyAdminLeft() {
        User viewerUser = users.add(user("bob", true, RoleId.VIEWER));

        UserAccount disabled =
                service.update(UserId.newId(), viewerUser.id(), Optional.empty(), Optional.of(false), null);

        assertThat(disabled.enabled()).isFalse();
    }

    @Test
    void aDisabledAdminCanBeDemotedAndEnabledAgain() {
        User off = users.add(user("off", false, RoleId.ADMIN));
        UserId manager = UserId.newId();

        service.update(manager, off.id(), Optional.of(Set.of(RoleId.VIEWER)), Optional.empty(), null);
        UserAccount enabled = service.update(manager, off.id(), Optional.empty(), Optional.of(true), null);

        assertThat(enabled.enabled()).isTrue();
    }

    @Test
    void resetWithAnEnteredPasswordFlagsTheUserAndClearsTheLock() {
        User locked = users.add(user("bob", true, RoleId.VIEWER).withFailedLogin(2, Optional.of(NOW.plusSeconds(600))));

        Optional<String> generated = service.resetPassword(locked.id(), Optional.of("another-long-password"));

        assertThat(generated).isEmpty();
        User stored = users.findById(locked.id()).orElseThrow();
        assertThat(stored.passwordHash()).isEqualTo(new PasswordHash("hash:another-long-password"));
        assertThat(stored.mustChangePassword()).isTrue();
        assertThat(stored.failedLoginCount()).isZero();
        assertThat(stored.lockedUntil()).isEmpty();
        assertThat(stored.enabled()).isTrue();
    }

    @Test
    void resetWithoutAPasswordGeneratesOne() {
        User target = users.add(user("bob", true, RoleId.VIEWER));

        Optional<String> generated = service.resetPassword(target.id(), Optional.empty());

        assertThat(generated).contains("generated-password");
        User stored = users.findById(target.id()).orElseThrow();
        assertThat(stored.passwordHash()).isEqualTo(new PasswordHash("hash:generated-password"));
        assertThat(stored.mustChangePassword()).isTrue();
    }

    @Test
    void resetDoesNotEnableADisabledUser() {
        User off = users.add(user("off", false, RoleId.VIEWER));

        service.resetPassword(off.id(), Optional.empty());

        assertThat(users.findById(off.id()).orElseThrow().enabled()).isFalse();
    }

    @Test
    void resetRejectsAWeakPasswordAndChangesNothing() {
        User target = users.add(user("bob", true, RoleId.VIEWER));

        assertThatThrownBy(() -> service.resetPassword(target.id(), Optional.of("bob")))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.WEAK_PASSWORD));
        assertThat(users.saved).isEmpty();
    }

    @Test
    void resetOfAnUnknownUserIsUserNotFound() {
        assertThatThrownBy(() -> service.resetPassword(UserId.newId(), Optional.empty()))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.USER_NOT_FOUND));
    }

    @Test
    void aLockingFailureOnResetBecomesConcurrentUpdate() {
        User target = users.add(user("bob", true, RoleId.VIEWER));
        users.failSaves = 1;

        assertThatThrownBy(() -> service.resetPassword(target.id(), Optional.empty()))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.CONCURRENT_UPDATE));
    }

    @Test
    void getGivesTheRoleNamesSortedAndTheLockAsOfNow() {
        User lockedUser = users.add(user("bob", true, RoleId.VIEWER, RoleId.ADMIN, RoleId.ANALYST)
                .withFailedLogin(0, Optional.of(NOW.plusSeconds(60)))
                .withLastLogin(NOW.minusSeconds(5)));

        UserAccount account = service.get(lockedUser.id());

        assertThat(account.roles()).extracting(Role::name).containsExactly("ADMIN", "ANALYST", "VIEWER");
        assertThat(account.locked()).isTrue();
        assertThat(account.lockedUntil()).contains(NOW.plusSeconds(60));
        assertThat(account.lastLoginAt()).contains(NOW.minusSeconds(5));
        assertThat(account.id()).isEqualTo(lockedUser.id());
        assertThat(account.version()).isEqualTo(lockedUser.version());
    }

    @Test
    void anExpiredLockReadsAsNotLocked() {
        User expired = users.add(user("bob", true).withFailedLogin(0, Optional.of(NOW.minusSeconds(1))));

        UserAccount account = service.get(expired.id());

        assertThat(account.locked()).isFalse();
        assertThat(account.lockedUntil()).contains(NOW.minusSeconds(1));
    }

    @Test
    void getOfAnUnknownUserIsUserNotFound() {
        assertThatThrownBy(() -> service.get(UserId.newId()))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.USER_NOT_FOUND));
    }

    @Test
    void listGivesEveryUserWithItsRoleNames() {
        users.add(user("bob", true, RoleId.VIEWER));
        users.add(user("alice", false, RoleId.ADMIN));

        List<UserAccount> accounts = service.list();

        assertThat(accounts).extracting(a -> a.username().value()).containsExactly("alice", "bob");
        assertThat(accounts.getFirst().roles()).extracting(Role::name).containsExactly("ADMIN");
        assertThat(accounts.getFirst().enabled()).isFalse();
        assertThat(accounts.getLast().roles()).extracting(Role::name).containsExactly("VIEWER");
    }

    private static Role role(RoleId id, String name, Permission... permissions) {
        return new Role(id, name, true, name, Set.of(permissions), 1L);
    }

    private static User user(String name, boolean enabled, RoleId... roleIds) {
        return new User(
                UserId.newId(),
                new Username(name),
                new PasswordHash("hash:old"),
                enabled,
                false,
                0,
                Optional.empty(),
                Optional.empty(),
                NOW,
                NOW,
                Set.of(roleIds),
                1L);
    }

    private static final class FixedGenerator extends OneTimePasswordService {

        private int lastLength;

        @Override
        String generate(int length) {
            lastLength = length;
            return "generated-password";
        }
    }

    private static final class FakeHasher implements PasswordHasherPort {

        @Override
        public PasswordHash hash(CharSequence rawPassword) {
            return new PasswordHash("hash:" + rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, PasswordHash hash) {
            return hash.value().equals("hash:" + rawPassword);
        }

        @Override
        public boolean needsRehash(PasswordHash hash) {
            return false;
        }
    }

    private static final class FakeUserRepository implements UserRepositoryPort {

        private final List<User> users = new ArrayList<>();
        private final List<User> saved = new ArrayList<>();
        private int failSaves;

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
                            NOW,
                            NOW,
                            user.roleIds(),
                            1L)
                    : user;
            saved.add(stored);
            users.removeIf(u -> u.id().equals(stored.id()));
            users.add(stored);
            return stored;
        }
    }
}
