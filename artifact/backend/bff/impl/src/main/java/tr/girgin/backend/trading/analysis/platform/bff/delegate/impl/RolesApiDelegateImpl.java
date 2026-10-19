package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.RolesApiDelegate;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RoleDto;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.RequiresPermission;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.RoleToRoleDtoMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.ManageRolesUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.RoleSummary;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;

@Service
class RolesApiDelegateImpl implements RolesApiDelegate {

    private final ManageRolesUseCase manageRoles;
    private final RoleToRoleDtoMapper roleMapper;

    RolesApiDelegateImpl(ManageRolesUseCase manageRoles, RoleToRoleDtoMapper roleMapper) {
        this.manageRoles = manageRoles;
        this.roleMapper = roleMapper;
    }

    @Override
    @RequiresPermission({Permission.USER_READ, Permission.ROLE_MANAGE})
    public List<RoleDto> listRoles() {
        return manageRoles.list().stream()
                .map(RoleSummary::role)
                .map(roleMapper::map)
                .toList();
    }
}
