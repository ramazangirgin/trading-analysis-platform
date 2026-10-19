package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.CreateUserRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.CreatedUserDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.OneTimePasswordDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.ResetPasswordRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.UpdateUserRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.UserDto;

@RestController
@RequestMapping("/api/users")
public class UsersApiController {

    private final UsersApiDelegate delegate;

    public UsersApiController(UsersApiDelegate delegate) {
        this.delegate = delegate;
    }

    @GetMapping
    public List<UserDto> listUsers() {
        return delegate.listUsers();
    }

    @GetMapping("/{id}")
    public UserDto getUser(@PathVariable String id) {
        return delegate.getUser(id);
    }

    @PostMapping
    public ResponseEntity<CreatedUserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        return delegate.createUser(request);
    }

    @PatchMapping("/{id}")
    public UserDto updateUser(
            @PathVariable String id, @Valid @RequestBody UpdateUserRequest request, HttpServletRequest http) {
        return delegate.updateUser(id, request, http);
    }

    @PostMapping("/{id}/password-reset")
    public OneTimePasswordDto resetPassword(@PathVariable String id, @Valid @RequestBody ResetPasswordRequest request) {
        return delegate.resetPassword(id, request);
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<Void> unlock(@PathVariable String id) {
        return delegate.unlock(id);
    }
}
