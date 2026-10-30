package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;

class UserChangeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    private final UserChangeService service = new UserChangeService();

    private static User user(Set<RoleId> roleIds) {
        return new User(
                new UserId("u1"),
                new Username("alice"),
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
    void newIdsAreDistinct() {
        assertThat(service.newId()).isNotEqualTo(service.newId());
    }

    @Test
    void keepsAuditFieldsNullBeforeTheFirstSave() {
        User unsaved = new User(
                new UserId("u1"),
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

        User changed = service.withRoleIds(unsaved, Set.of(new RoleId("r1")));

        assertThat(changed.createdAt()).isNull();
        assertThat(changed.updatedAt()).isNull();
        assertThat(changed.version()).isNull();
    }

    @Test
    void copiesTheRoleSetAndKeepsItImmutable() {
        Set<RoleId> roles = new HashSet<>(Set.of(new RoleId("r1")));
        User changed = service.withRoleIds(user(Set.of()), roles);

        roles.add(new RoleId("r2"));

        assertThat(changed.roleIds()).hasSize(1);
        assertThatThrownBy(() -> changed.roleIds().add(new RoleId("r3")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withRoleIdsReplacesOnlyTheRoles() {
        User user = user(Set.of());
        Set<RoleId> roles = Set.of(new RoleId("r1"));

        assertThat(service.withRoleIds(user, roles))
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
    void withEnabledReplacesOnlyTheFlag() {
        User user = user(Set.of());

        assertThat(service.withEnabled(user, false))
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
    void withPasswordReplacesOnlyTheHashAndTheFlag() {
        Set<RoleId> roles = Set.of(new RoleId("r1"));
        User user = user(roles);
        PasswordHash hash = new PasswordHash("{noop}y");

        assertThat(service.withPassword(user, hash, true))
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
        Set<RoleId> roles = Set.of(new RoleId("r1"));
        User user = user(roles);
        Instant until = NOW.plusSeconds(900);

        assertThat(service.withFailedLogin(user, 4, Optional.of(until)))
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
    void withLastLoginReplacesOnlyTheTimeOfTheLastLogin() {
        Set<RoleId> roles = Set.of(new RoleId("r1"));
        User user = user(roles);
        Instant at = NOW.plusSeconds(60);

        assertThat(service.withLastLogin(user, at))
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
}
