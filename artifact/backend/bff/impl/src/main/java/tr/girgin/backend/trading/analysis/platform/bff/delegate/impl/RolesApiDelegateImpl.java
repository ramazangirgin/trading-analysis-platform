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
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.ManageRolesPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;

@Service
class RolesApiDelegateImpl implements RolesApiDelegate {

    private final ManageRolesPort manageRolesPort;
    private final RoleSummaryToRoleDtoMapper roleSummaryToRoleDtoMapper;
    private final PermissionEnumDtoToPermissionMapper permissionEnumDtoToPermissionMapper;
    private final SessionAuthorities sessionAuthorities;

    RolesApiDelegateImpl(
            ManageRolesPort manageRolesPort,
            RoleSummaryToRoleDtoMapper roleSummaryToRoleDtoMapper,
            PermissionEnumDtoToPermissionMapper permissionEnumDtoToPermissionMapper,
            SessionAuthorities sessionAuthorities) {
        this.sessionAuthorities = sessionAuthorities;
        this.manageRolesPort = manageRolesPort;
        this.roleSummaryToRoleDtoMapper = roleSummaryToRoleDtoMapper;
        this.permissionEnumDtoToPermissionMapper = permissionEnumDtoToPermissionMapper;
    }

    @Override
    @RequiresPermission({Permission.USER_READ, Permission.ROLE_MANAGE})
    public List<RoleDto> listRoles() {
        return manageRolesPort.list().stream()
                .map(roleSummaryToRoleDtoMapper::map)
                .toList();
    }

    @Override
    @RequiresPermission({Permission.USER_READ, Permission.ROLE_MANAGE})
    public RoleDto getRole(String id) {
        return roleSummaryToRoleDtoMapper.map(manageRolesPort.get(new RoleId(id)));
    }

    @Override
    @RequiresPermission(Permission.ROLE_MANAGE)
    public ResponseEntity<RoleDto> createRole(CreateRoleRequest request) {
        RoleDto created = roleSummaryToRoleDtoMapper.map(
                manageRolesPort.create(request.name(), request.description(), permissions(request.permissions())));
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
        RoleDto updated = roleSummaryToRoleDtoMapper.map(manageRolesPort.update(
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
        manageRolesPort.delete(new RoleId(id));
        return ResponseEntity.noContent().build();
    }

    private Set<Permission> permissions(List<PermissionEnumDto> dtos) {
        return dtos.stream().map(permissionEnumDtoToPermissionMapper::map).collect(Collectors.toSet());
    }
}
