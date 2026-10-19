package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    private static User user(UserId id, Username username, Set<RoleId> roleIds) {
        return new User(
                id,
                username,
                new PasswordHash("{noop}x"),
                true,
                false,
                0,
                Optional.empty(),
                Optional.empty(),
                NOW,
                NOW,
                roleIds,
                3L);
    }

    @Test
    void rejectsNullFields() {
        Username name = new Username("alice");

        assertThatThrownBy(() -> user(null, name, Set.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> user(UserId.newId(), null, Set.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> user(UserId.newId(), name, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void auditFieldsAreNullBeforeTheFirstSave() {
        User user = new User(
                UserId.newId(),
                new Username("alice"),
                new PasswordHash("{noop}x"),
                true,
                false,
                0,
                Optional.empty(),
                Optional.empty(),
                null,
                null,
                Set.of(),
                null);

        assertThat(user.createdAt()).isNull();
        assertThat(user.updatedAt()).isNull();
        assertThat(user.withRoleIds(Set.of(RoleId.newId())).createdAt()).isNull();
    }

    @Test
    void copiesTheRoleSetAndKeepsItImmutable() {
        Set<RoleId> roles = new HashSet<>(Set.of(RoleId.newId()));
        User user = user(UserId.newId(), new Username("alice"), roles);

        roles.add(RoleId.newId());

        assertThat(user.roleIds()).hasSize(1);
        assertThatThrownBy(() -> user.roleIds().add(RoleId.newId())).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withPasswordReplacesOnlyTheHashAndTheFlag() {
        Set<RoleId> roles = Set.of(RoleId.newId());
        User user = user(UserId.newId(), new Username("alice"), roles);
        PasswordHash hash = new PasswordHash("{noop}y");

        assertThat(user.withPassword(hash, true))
                .isEqualTo(new User(
                        user.id(),
                        user.username(),
                        hash,
                        true,
                        true,
                        0,
                        Optional.empty(),
                        Optional.empty(),
                        NOW,
                        NOW,
                        roles,
                        3L));
    }

    @Test
    void withFailedLoginReplacesOnlyTheCountAndTheLock() {
        Set<RoleId> roles = Set.of(RoleId.newId());
        User user = user(UserId.newId(), new Username("alice"), roles);
        Instant until = NOW.plusSeconds(900);

        assertThat(user.withFailedLogin(4, Optional.of(until)))
                .isEqualTo(new User(
                        user.id(),
                        user.username(),
                        user.passwordHash(),
                        true,
                        false,
                        4,
                        Optional.of(until),
                        Optional.empty(),
                        NOW,
                        NOW,
                        roles,
                        3L));
    }

    @Test
    void withLoginSucceededClearsTheCountAndTheLockAndKeepsTheRest() {
        Set<RoleId> roles = Set.of(RoleId.newId());
        User user = user(UserId.newId(), new Username("alice"), roles)
                .withFailedLogin(2, Optional.of(NOW.plusSeconds(900)));

        assertThat(user.withLoginSucceeded())
                .isEqualTo(new User(
                        user.id(),
                        user.username(),
                        user.passwordHash(),
                        true,
                        false,
                        0,
                        Optional.empty(),
                        Optional.empty(),
                        NOW,
                        NOW,
                        roles,
                        3L));
    }

    @Test
    void withLastLoginReplacesOnlyTheTimeOfTheLastLogin() {
        Set<RoleId> roles = Set.of(RoleId.newId());
        User user = user(UserId.newId(), new Username("alice"), roles);
        Instant at = NOW.plusSeconds(60);

        assertThat(user.withLastLogin(at))
                .isEqualTo(new User(
                        user.id(),
                        user.username(),
                        user.passwordHash(),
                        true,
                        false,
                        0,
                        Optional.empty(),
                        Optional.of(at),
                        NOW,
                        NOW,
                        roles,
                        3L));
    }

    @Test
    void withEnabledReplacesOnlyTheFlag() {
        User user = user(UserId.newId(), new Username("alice"), Set.of());

        assertThat(user.withEnabled(false))
                .isEqualTo(new User(
                        user.id(),
                        user.username(),
                        user.passwordHash(),
                        false,
                        false,
                        0,
                        Optional.empty(),
                        Optional.empty(),
                        NOW,
                        NOW,
                        Set.of(),
                        3L));
    }

    @Test
    void aLockHoldsUntilItsInstantIsReached() {
        User user = user(UserId.newId(), new Username("alice"), Set.of());
        Instant until = NOW.plusSeconds(900);

        assertThat(user.isLockedAt(NOW)).isFalse();
        assertThat(user.withFailedLogin(0, Optional.of(until)).isLockedAt(until.minusMillis(1)))
                .isTrue();
        assertThat(user.withFailedLogin(0, Optional.of(until)).isLockedAt(until))
                .isFalse();
    }

    @Test
    void withRoleIdsReplacesOnlyTheRoles() {
        User user = user(UserId.newId(), new Username("alice"), Set.of());
        Set<RoleId> roles = Set.of(RoleId.newId());

        assertThat(user.withRoleIds(roles))
                .isEqualTo(new User(
                        user.id(),
                        user.username(),
                        user.passwordHash(),
                        true,
                        false,
                        0,
                        Optional.empty(),
                        Optional.empty(),
                        NOW,
                        NOW,
                        roles,
                        3L));
    }
}
