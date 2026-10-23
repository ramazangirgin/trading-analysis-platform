package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.RolesApiDelegate;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.error.ApiException;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.CreateRoleRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PermissionEnumDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RoleDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.UpdateRoleRequest;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.RequiresPermission;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.SessionAuthorities;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.PermissionEnumDtoToPermissionMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.RoleSummaryToRoleDtoMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.ManageRolesUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;

@Service
class RolesApiDelegateImpl implements RolesApiDelegate {

    private final ManageRolesUseCase manageRoles;
    private final RoleSummaryToRoleDtoMapper roleMapper;
    private final PermissionEnumDtoToPermissionMapper permissionMapper;
    private final SessionAuthorities sessionAuthorities;

    RolesApiDelegateImpl(
            ManageRolesUseCase manageRoles,
            RoleSummaryToRoleDtoMapper roleMapper,
            PermissionEnumDtoToPermissionMapper permissionMapper,
            SessionAuthorities sessionAuthorities) {
        this.sessionAuthorities = sessionAuthorities;
        this.manageRoles = manageRoles;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
    }

    @Override
    @RequiresPermission({Permission.USER_READ, Permission.ROLE_MANAGE})
    public List<RoleDto> listRoles() {
        return manageRoles.list().stream().map(roleMapper::map).toList();
    }

    @Override
    @RequiresPermission({Permission.USER_READ, Permission.ROLE_MANAGE})
    public RoleDto getRole(String id) {
        return roleMapper.map(manageRoles.get(new RoleId(id)));
    }

    @Override
    @RequiresPermission(Permission.ROLE_MANAGE)
    public ResponseEntity<RoleDto> createRole(CreateRoleRequest request) {
        RoleDto created = roleMapper.map(
                manageRoles.create(request.name(), request.description(), permissions(request.permissions())));
        return ResponseEntity.created(URI.create("/api/roles/" + created.id())).body(created);
    }

    @Override
    @RequiresPermission(Permission.ROLE_MANAGE)
    public RoleDto updateRole(String id, UpdateRoleRequest request) {
        if (request.name() == null && request.description() == null && request.permissions() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "invalid_request",
                    "Give name, description, permissions or any of them",
                    Map.of("fields", List.of("name", "description", "permissions")));
        }
        RoleDto updated = roleMapper.map(manageRoles.update(
                new RoleId(id),
                Optional.ofNullable(request.name()),
                Optional.ofNullable(request.description()),
                Optional.ofNullable(request.permissions()).map(this::permissions),
                request.version()));
        sessionAuthorities.rolesChanged();
        return updated;
    }

    @Override
    @RequiresPermission(Permission.ROLE_MANAGE)
    public ResponseEntity<Void> deleteRole(String id) {
        manageRoles.delete(new RoleId(id));
        return ResponseEntity.noContent().build();
    }

    private Set<Permission> permissions(List<PermissionEnumDto> dtos) {
        return dtos.stream().map(permissionMapper::map).collect(Collectors.toSet());
    }
}
