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
                id, username, new PasswordHash("{noop}x"), true, false, 0, Optional.empty(), NOW, NOW, roleIds, 3L);
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
                        NOW,
                        NOW,
                        roles,
                        3L));
    }
}
