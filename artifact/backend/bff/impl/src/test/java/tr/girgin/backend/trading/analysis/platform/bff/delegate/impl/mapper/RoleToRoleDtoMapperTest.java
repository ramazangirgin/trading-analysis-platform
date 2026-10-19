package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PermissionDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RoleDto;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;

class RoleToRoleDtoMapperTest {

    private final RoleToRoleDtoMapper mapper = new RoleToRoleDtoMapperImpl(new PermissionToPermissionDtoMapperImpl());

    @Test
    void mapsTheRoleWithItsPermissionsSortedByKey() {
        Role role = new Role(
                RoleId.ANALYST,
                "ANALYST",
                true,
                "Runs analyses",
                Set.of(Permission.USER_READ, Permission.ANALYSIS_RUN, Permission.PRESET_READ),
                3L);

        RoleDto dto = mapper.map(role);

        assertThat(dto.id()).isEqualTo("ANALYST");
        assertThat(dto.name()).isEqualTo("ANALYST");
        assertThat(dto.builtIn()).isTrue();
        assertThat(dto.description()).isEqualTo("Runs analyses");
        assertThat(dto.permissions())
                .containsExactly(PermissionDto.ANALYSIS_RUN, PermissionDto.PRESET_READ, PermissionDto.USER_READ);
    }
}
