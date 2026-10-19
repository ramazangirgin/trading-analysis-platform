package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.CreateRoleRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RoleDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.UpdateRoleRequest;

@RestController
@RequestMapping("/api/roles")
public class RolesApiController {

    private final RolesApiDelegate delegate;

    public RolesApiController(RolesApiDelegate delegate) {
        this.delegate = delegate;
    }

    @GetMapping
    public List<RoleDto> listRoles() {
        return delegate.listRoles();
    }

    @GetMapping("/{id}")
    public RoleDto getRole(@PathVariable String id) {
        return delegate.getRole(id);
    }

    @PostMapping
    public ResponseEntity<RoleDto> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return delegate.createRole(request);
    }

    @PatchMapping("/{id}")
    public RoleDto updateRole(@PathVariable String id, @Valid @RequestBody UpdateRoleRequest request) {
        return delegate.updateRole(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable String id) {
        return delegate.deleteRole(id);
    }
}
