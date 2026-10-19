package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import java.util.List;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RoleDto;

public interface RolesApiDelegate {

    List<RoleDto> listRoles();
}
