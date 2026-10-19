package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.RolesApiDelegate;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RoleDto;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.RequiresPermission;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.RoleToRoleDtoMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.ListRolesUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;

@Service
class RolesApiDelegateImpl implements RolesApiDelegate {

    private final ListRolesUseCase listRoles;
    private final RoleToRoleDtoMapper roleMapper;

    RolesApiDelegateImpl(ListRolesUseCase listRoles, RoleToRoleDtoMapper roleMapper) {
        this.listRoles = listRoles;
        this.roleMapper = roleMapper;
    }

    @Override
    @RequiresPermission({Permission.USER_READ, Permission.ROLE_MANAGE})
    public ResponseEntity<List<RoleDto>> listRoles() {
        return ResponseEntity.ok(listRoles.list().stream().map(roleMapper::map).toList());
    }
}
