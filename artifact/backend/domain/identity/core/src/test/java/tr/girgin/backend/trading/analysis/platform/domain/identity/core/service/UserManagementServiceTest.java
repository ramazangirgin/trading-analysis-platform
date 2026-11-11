package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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

class UserManagementServiceTest {

    private static final UserChangeService CHANGES = new UserChangeService();
    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    private final FakeUserStore fakeUserStore = new FakeUserStore(NOW);
    private final InMemoryRoleRepository inMemoryRoleRepository = new InMemoryRoleRepository();
    private final FixedGenerator generator = new FixedGenerator();
    private final UserManagementService userManagementService = new UserManagementService(
            fakeUserStore,
            inMemoryRoleRepository,
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
        List.of(viewer, admin, analyst).forEach(inMemoryRoleRepository::add);
    }

    private static UserId newId() {
        return new UserId(UUID.randomUUID().toString());
    }

    @Test
    void createWithAnEnteredPasswordStoresItHashedAndFlagged() {
        CreatedUserAccount created =
                userManagementService.create("alice", Set.of(RoleId.ANALYST), Optional.of("a-long-enough-password"));

        assertThat(created.generatedPassword()).isEmpty();
        assertThat(created.account().username()).isEqualTo(new Username("alice"));
        assertThat(created.account().enabled()).isTrue();
        assertThat(created.account().mustChangePassword()).isTrue();
        assertThat(created.account().roles()).extracting(Role::name).containsExactly("ANALYST");
        assertThat(created.account().lastLoginAt()).isEmpty();
        User stored = fakeUserStore.findByUsername(new Username("alice")).orElseThrow();
        assertThat(stored.passwordHash()).isEqualTo(new PasswordHash("hash:a-long-enough-password"));
        assertThat(stored.mustChangePassword()).isTrue();
        assertThat(stored.failedLoginCount()).isZero();
        assertThat(stored.lockedUntil()).isEmpty();
    }

    @Test
    void createWithoutAPasswordGeneratesOne() {
        CreatedUserAccount created = userManagementService.create("alice", Set.of(), Optional.empty());

        assertThat(created.generatedPassword()).contains("generated-password");
        assertThat(generator.lastLength).isEqualTo(24);
        User stored = fakeUserStore.findByUsername(new Username("alice")).orElseThrow();
        assertThat(stored.passwordHash()).isEqualTo(new PasswordHash("hash:generated-password"));
        assertThat(stored.mustChangePassword()).isTrue();
    }

    @Test
    void aBlankPasswordIsTheSameAsNone() {
        CreatedUserAccount created = userManagementService.create("alice", Set.of(), Optional.of("  "));

        assertThat(created.generatedPassword()).isPresent();
    }

    @Test
    void aGeneratedPasswordIsAsLongAsThePolicyAsksForWhenThatIsMore() {
        UserManagementService strictUserManagementService = new UserManagementService(
                fakeUserStore,
                inMemoryRoleRepository,
                new FakeHasher(),
                new PasswordPolicyService(40, "password1234567\n"),
                generator,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new UserChangeService(),
                new UsernameService(),
                new LoginLockoutService(5, Duration.ofMinutes(15)));

        strictUserManagementService.create("alice", Set.of(), Optional.empty());

        assertThat(generator.lastLength).isEqualTo(40);
    }

    @Test
    void createRejectsAWeakEnteredPassword() {
        assertThatThrownBy(() -> userManagementService.create("alice", Set.of(), Optional.of("short")))
                .isInstanceOfSatisfying(IdentityException.class, e -> {
                    assertThat(e.error()).isEqualTo(IdentityError.WEAK_PASSWORD);
                    assertThat(e.params()).containsKey("reasons");
                });
        assertThat(fakeUserStore.users()).isEmpty();
    }

    @Test
    void createRejectsAnInvalidUsername() {
        assertThatThrownBy(() -> userManagementService.create("a b", Set.of(), Optional.empty()))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.INVALID_USERNAME));
    }

    @Test
    void createRejectsAnUnknownRole() {
        RoleId unknown = new RoleId("nope");

        assertThatThrownBy(() -> userManagementService.create("alice", Set.of(unknown), Optional.empty()))
                .isInstanceOfSatisfying(IdentityException.class, e -> {
                    assertThat(e.error()).isEqualTo(IdentityError.UNKNOWN_ROLE);
                    assertThat(e.params()).containsEntry("roleId", "nope");
                });
        assertThat(fakeUserStore.users()).isEmpty();
    }

    @Test
    void createRejectsATakenUsername() {
        userManagementService.create("alice", Set.of(), Optional.empty());

        assertThatThrownBy(() -> userManagementService.create("ALICE", Set.of(), Optional.empty()))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.USERNAME_TAKEN));
    }

    @Test
    void updateChangesTheRoles() {
        User target = fakeUserStore.add(user("bob", true, RoleId.VIEWER));

        UserAccount updated = userManagementService.update(
                newId(), target.id(), Optional.of(Set.of(RoleId.ANALYST)), Optional.empty(), null);

        assertThat(updated.roles()).extracting(Role::name).containsExactly("ANALYST");
        assertThat(fakeUserStore.findById(target.id()).orElseThrow().roleIds()).containsExactly(RoleId.ANALYST);
        assertThat(updated.enabled()).isTrue();
    }

    @Test
    void updateDisablesAndEnables() {
        User target = fakeUserStore.add(user("bob", true, RoleId.VIEWER));
        UserId actor = newId();

        UserAccount disabled =
                userManagementService.update(actor, target.id(), Optional.empty(), Optional.of(false), null);
        assertThat(disabled.enabled()).isFalse();
        assertThat(fakeUserStore.findById(target.id()).orElseThrow().enabled()).isFalse();

        UserAccount enabled =
                userManagementService.update(actor, target.id(), Optional.empty(), Optional.of(true), null);
        assertThat(enabled.enabled()).isTrue();
    }

    @Test
    void anUpdateThatChangesNothingWritesNothing() {
        User target = fakeUserStore.add(user("bob", true, RoleId.VIEWER));

        userManagementService.update(newId(), target.id(), Optional.empty(), Optional.empty(), null);
        userManagementService.update(newId(), target.id(), Optional.of(Set.of(RoleId.VIEWER)), Optional.of(true), null);

        assertThat(fakeUserStore.saved()).isEmpty();
    }

    @Test
    void updateRejectsAnUnknownRole() {
        User target = fakeUserStore.add(user("bob", true));

        assertThatThrownBy(() -> userManagementService.update(
                        newId(), target.id(), Optional.of(Set.of(new RoleId("nope"))), Optional.empty(), null))
                .isInstanceOfSatisfying(IdentityException.class, e -> {
                    assertThat(e.error()).isEqualTo(IdentityError.UNKNOWN_ROLE);
                    assertThat(e.params()).containsEntry("roleId", "nope");
                });
    }

    @Test
    void updateOfAnUnknownUserIsUserNotFound() {
        UserId id = newId();

        assertThatThrownBy(() -> userManagementService.update(newId(), id, Optional.empty(), Optional.of(false), null))
                .isInstanceOfSatisfying(IdentityException.class, e -> {
                    assertThat(e.error()).isEqualTo(IdentityError.USER_NOT_FOUND);
                    assertThat(e.params()).containsEntry("id", id.value());
                });
    }

    @Test
    void aStaleVersionIsAConcurrentUpdate() {
        User target = fakeUserStore.add(user("bob", true, RoleId.VIEWER));

        assertThatThrownBy(() -> userManagementService.update(
                        newId(), target.id(), Optional.empty(), Optional.of(false), target.version() + 1))
                .isInstanceOfSatisfying(IdentityException.class, e -> {
                    assertThat(e.error()).isEqualTo(IdentityError.CONCURRENT_UPDATE);
                    assertThat(e.params()).containsEntry("id", target.id().value());
                });
        assertThat(fakeUserStore.saved()).isEmpty();
    }

    @Test
    void theCurrentVersionIsAccepted() {
        User target = fakeUserStore.add(user("bob", true, RoleId.VIEWER));

        UserAccount updated = userManagementService.update(
                newId(), target.id(), Optional.empty(), Optional.of(false), target.version());

        assertThat(updated.enabled()).isFalse();
    }

    @Test
    void aLockingFailureOnSaveBecomesConcurrentUpdate() {
        User target = fakeUserStore.add(user("bob", true, RoleId.VIEWER));
        fakeUserStore.failNextSaves(1);

        assertThatThrownBy(() ->
                        userManagementService.update(newId(), target.id(), Optional.empty(), Optional.of(false), null))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.CONCURRENT_UPDATE));
    }

    @Test
    void anAdminCannotDisableThemselves() {
        User self = fakeUserStore.add(user("root", true, RoleId.ADMIN));
        fakeUserStore.add(user("other", true, RoleId.ADMIN));

        assertThatThrownBy(() ->
                        userManagementService.update(self.id(), self.id(), Optional.empty(), Optional.of(false), null))
                .isInstanceOfSatisfying(
                        IdentityException.class,
                        e -> assertThat(e.error()).isEqualTo(IdentityError.CANNOT_DISABLE_SELF));
    }

    @Test
    void anAdminCannotRemoveTheirOwnAdminRole() {
        User self = fakeUserStore.add(user("root", true, RoleId.ADMIN));
        fakeUserStore.add(user("other", true, RoleId.ADMIN));

        assertThatThrownBy(() -> userManagementService.update(
                        self.id(), self.id(), Optional.of(Set.of(RoleId.VIEWER)), Optional.empty(), null))
                .isInstanceOfSatisfying(
                        IdentityException.class,
                        e -> assertThat(e.error()).isEqualTo(IdentityError.CANNOT_REMOVE_OWN_ADMIN));
    }

    @Test
    void anAdminCanChangeTheirOwnOtherRoles() {
        User self = fakeUserStore.add(user("root", true, RoleId.ADMIN));

        UserAccount updated = userManagementService.update(
                self.id(), self.id(), Optional.of(Set.of(RoleId.ADMIN, RoleId.VIEWER)), Optional.empty(), null);

        assertThat(updated.roles()).extracting(Role::name).containsExactly("ADMIN", "VIEWER");
    }

    @Test
    void theLastEnabledAdminCannotBeDisabled() {
        User onlyAdmin = fakeUserStore.add(user("root", true, RoleId.ADMIN));
        fakeUserStore.add(user("off", false, RoleId.ADMIN));
        UserId manager = newId();

        assertThatThrownBy(() -> userManagementService.update(
                        manager, onlyAdmin.id(), Optional.empty(), Optional.of(false), null))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.LAST_ADMIN));
        assertThat(fakeUserStore.findById(onlyAdmin.id()).orElseThrow().enabled())
                .isTrue();
    }

    @Test
    void theLastEnabledAdminCannotBeDemoted() {
        User onlyAdmin = fakeUserStore.add(user("root", true, RoleId.ADMIN));
        UserId manager = newId();

        assertThatThrownBy(() -> userManagementService.update(
                        manager, onlyAdmin.id(), Optional.of(Set.of(RoleId.VIEWER)), Optional.empty(), null))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.LAST_ADMIN));
    }

    @Test
    void anotherEnabledAdminLetsBothChangesThrough() {
        User first = fakeUserStore.add(user("root", true, RoleId.ADMIN));
        User second = fakeUserStore.add(user("second", true, RoleId.ADMIN));
        UserId manager = newId();

        UserAccount demoted = userManagementService.update(
                manager, first.id(), Optional.of(Set.of(RoleId.VIEWER)), Optional.empty(), null);
        assertThat(demoted.roles()).extracting(Role::name).containsExactly("VIEWER");

        assertThatThrownBy(() ->
                        userManagementService.update(manager, second.id(), Optional.empty(), Optional.of(false), null))
                .isInstanceOf(IdentityException.class);
        userManagementService.update(manager, first.id(), Optional.of(Set.of(RoleId.ADMIN)), Optional.empty(), null);
        UserAccount disabled =
                userManagementService.update(manager, second.id(), Optional.empty(), Optional.of(false), null);
        assertThat(disabled.enabled()).isFalse();
    }

    @Test
    void aDisabledAdminDoesNotCount() {
        User enabledAdmin = fakeUserStore.add(user("root", true, RoleId.ADMIN));
        fakeUserStore.add(user("off", false, RoleId.ADMIN));

        assertThatThrownBy(() -> userManagementService.update(
                        newId(), enabledAdmin.id(), Optional.empty(), Optional.of(false), null))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.LAST_ADMIN));
    }

    @Test
    void aNonAdminCanBeDisabledWithoutAnyAdminLeft() {
        User viewerUser = fakeUserStore.add(user("bob", true, RoleId.VIEWER));

        UserAccount disabled =
                userManagementService.update(newId(), viewerUser.id(), Optional.empty(), Optional.of(false), null);

        assertThat(disabled.enabled()).isFalse();
    }

    @Test
    void aDisabledAdminCanBeDemotedAndEnabledAgain() {
        User off = fakeUserStore.add(user("off", false, RoleId.ADMIN));
        UserId manager = newId();

        userManagementService.update(manager, off.id(), Optional.of(Set.of(RoleId.VIEWER)), Optional.empty(), null);
        UserAccount enabled =
                userManagementService.update(manager, off.id(), Optional.empty(), Optional.of(true), null);

        assertThat(enabled.enabled()).isTrue();
    }

    @Test
    void resetWithAnEnteredPasswordFlagsTheUserAndClearsTheLock() {
        User locked = fakeUserStore.add(
                CHANGES.withFailedLogin(user("bob", true, RoleId.VIEWER), 2, Optional.of(NOW.plusSeconds(600))));

        Optional<String> generated =
                userManagementService.resetPassword(locked.id(), Optional.of("another-long-password"));

        assertThat(generated).isEmpty();
        User stored = fakeUserStore.findById(locked.id()).orElseThrow();
        assertThat(stored.passwordHash()).isEqualTo(new PasswordHash("hash:another-long-password"));
        assertThat(stored.mustChangePassword()).isTrue();
        assertThat(stored.failedLoginCount()).isZero();
        assertThat(stored.lockedUntil()).isEmpty();
        assertThat(stored.enabled()).isTrue();
    }

    @Test
    void resetWithoutAPasswordGeneratesOne() {
        User target = fakeUserStore.add(user("bob", true, RoleId.VIEWER));

        Optional<String> generated = userManagementService.resetPassword(target.id(), Optional.empty());

        assertThat(generated).contains("generated-password");
        User stored = fakeUserStore.findById(target.id()).orElseThrow();
        assertThat(stored.passwordHash()).isEqualTo(new PasswordHash("hash:generated-password"));
        assertThat(stored.mustChangePassword()).isTrue();
    }

    @Test
    void resetDoesNotEnableADisabledUser() {
        User off = fakeUserStore.add(user("off", false, RoleId.VIEWER));

        userManagementService.resetPassword(off.id(), Optional.empty());

        assertThat(fakeUserStore.findById(off.id()).orElseThrow().enabled()).isFalse();
    }

    @Test
    void resetRejectsAWeakPasswordAndChangesNothing() {
        User target = fakeUserStore.add(user("bob", true, RoleId.VIEWER));

        assertThatThrownBy(() -> userManagementService.resetPassword(target.id(), Optional.of("bob")))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.WEAK_PASSWORD));
        assertThat(fakeUserStore.saved()).isEmpty();
    }

    @Test
    void resetOfAnUnknownUserIsUserNotFound() {
        assertThatThrownBy(() -> userManagementService.resetPassword(newId(), Optional.empty()))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.USER_NOT_FOUND));
    }

    @Test
    void aLockingFailureOnResetBecomesConcurrentUpdate() {
        User target = fakeUserStore.add(user("bob", true, RoleId.VIEWER));
        fakeUserStore.failNextSaves(1);

        assertThatThrownBy(() -> userManagementService.resetPassword(target.id(), Optional.empty()))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.CONCURRENT_UPDATE));
    }

    @Test
    void getGivesTheRoleNamesSortedAndTheLockAsOfNow() {
        User lockedUser = fakeUserStore.add(CHANGES.withLastLogin(
                CHANGES.withFailedLogin(
                        user("bob", true, RoleId.VIEWER, RoleId.ADMIN, RoleId.ANALYST),
                        0,
                        Optional.of(NOW.plusSeconds(60))),
                NOW.minusSeconds(5)));

        UserAccount account = userManagementService.get(lockedUser.id());

        assertThat(account.roles()).extracting(Role::name).containsExactly("ADMIN", "ANALYST", "VIEWER");
        assertThat(account.locked()).isTrue();
        assertThat(account.lockedUntil()).contains(NOW.plusSeconds(60));
        assertThat(account.lastLoginAt()).contains(NOW.minusSeconds(5));
        assertThat(account.id()).isEqualTo(lockedUser.id());
        assertThat(account.version()).isEqualTo(lockedUser.version());
    }

    @Test
    void anExpiredLockReadsAsNotLocked() {
        User expired =
                fakeUserStore.add(CHANGES.withFailedLogin(user("bob", true), 0, Optional.of(NOW.minusSeconds(1))));

        UserAccount account = userManagementService.get(expired.id());

        assertThat(account.locked()).isFalse();
        assertThat(account.lockedUntil()).contains(NOW.minusSeconds(1));
    }

    @Test
    void getOfAnUnknownUserIsUserNotFound() {
        assertThatThrownBy(() -> userManagementService.get(newId()))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.USER_NOT_FOUND));
    }

    @Test
    void listGivesEveryUserWithItsRoleNames() {
        fakeUserStore.add(user("bob", true, RoleId.VIEWER));
        fakeUserStore.add(user("alice", false, RoleId.ADMIN));

        List<UserAccount> accounts = userManagementService.list();

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
                newId(),
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
}
