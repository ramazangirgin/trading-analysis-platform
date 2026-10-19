package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import java.util.List;
import org.springframework.http.ResponseEntity;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RoleDto;

public interface RolesApiDelegate {

    ResponseEntity<List<RoleDto>> listRoles();
}
