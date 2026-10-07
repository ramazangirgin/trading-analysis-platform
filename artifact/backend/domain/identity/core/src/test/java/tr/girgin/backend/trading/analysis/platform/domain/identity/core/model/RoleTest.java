package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void rejectsNullFields() {
        assertThatThrownBy(() -> new Role(null, "ADMIN", true, "", Set.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Role(RoleId.newId(), null, true, "", Set.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Role(RoleId.newId(), "ADMIN", true, null, Set.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Role(RoleId.newId(), "ADMIN", true, "", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void copiesThePermissionSetAndKeepsItImmutable() {
        Set<Permission> permissions = new HashSet<>(Set.of(Permission.ANALYSIS_RUN));
        Role role = new Role(RoleId.newId(), "ANALYST", false, "", permissions);

        permissions.add(Permission.USER_MANAGE);

        assertThat(role.permissions()).containsExactly(Permission.ANALYSIS_RUN);
        assertThatThrownBy(() -> role.permissions().add(Permission.USER_MANAGE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withPermissionsReplacesOnlyThePermissions() {
        Role role = new Role(RoleId.newId(), "ANALYST", false, "d", Set.of());

        assertThat(role.withPermissions(Set.of(Permission.ANALYSIS_READ)))
                .isEqualTo(new Role(role.id(), "ANALYST", false, "d", Set.of(Permission.ANALYSIS_READ)));
    }
}
