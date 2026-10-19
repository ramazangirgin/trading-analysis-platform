package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.RoleRepositoryPort;

class RoleQueryServiceTest {

    @Test
    void listsTheRolesSortedByName() {
        Role viewer = new Role(RoleId.VIEWER, "VIEWER", true, "Reads", Set.of(Permission.ANALYSIS_READ), 1L);
        Role admin = new Role(RoleId.ADMIN, "ADMIN", true, "Everything", Set.of(Permission.USER_MANAGE), 1L);
        Role analyst = new Role(RoleId.ANALYST, "ANALYST", true, "Runs", Set.of(), 1L);
        RoleQueryService service = new RoleQueryService(new FakeRoles(List.of(viewer, admin, analyst)));

        assertThat(service.list()).containsExactly(admin, analyst, viewer);
    }

    @Test
    void listsNothingWithoutRoles() {
        assertThat(new RoleQueryService(new FakeRoles(List.of())).list()).isEmpty();
    }

    private record FakeRoles(List<Role> roles) implements RoleRepositoryPort {

        @Override
        public Optional<Role> findById(RoleId id) {
            return roles.stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Role> findByName(String name) {
            return roles.stream().filter(r -> r.name().equals(name)).findFirst();
        }

        @Override
        public List<Role> findAll() {
            return new ArrayList<>(roles);
        }

        @Override
        public Role save(Role role) {
            throw new UnsupportedOperationException();
        }
    }
}
