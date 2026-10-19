package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import java.util.List;
import org.springframework.http.ResponseEntity;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.CreateRoleRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RoleDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.UpdateRoleRequest;

public interface RolesApiDelegate {

    List<RoleDto> listRoles();

    RoleDto getRole(String id);

    ResponseEntity<RoleDto> createRole(CreateRoleRequest request);

    RoleDto updateRole(String id, UpdateRoleRequest request);

    ResponseEntity<Void> deleteRole(String id);
}
