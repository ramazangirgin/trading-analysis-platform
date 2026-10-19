package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RoleDto;

@RestController
@RequestMapping("/api/roles")
public class RolesApiController {

    private final RolesApiDelegate delegate;

    public RolesApiController(RolesApiDelegate delegate) {
        this.delegate = delegate;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<RoleDto>> listRoles() {
        return delegate.listRoles();
    }
}
