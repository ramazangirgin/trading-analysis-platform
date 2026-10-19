package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.ChangePasswordRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.CurrentUserDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.LoginRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final AuthApiDelegate delegate;

    public AuthApiController(AuthApiDelegate delegate) {
        this.delegate = delegate;
    }

    @PostMapping("/login")
    public ResponseEntity<CurrentUserDto> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        return delegate.login(request, http, response);
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserDto> me(HttpServletRequest http, HttpServletResponse response) {
        return delegate.me(http, response);
    }

    @PostMapping("/password")
    public ResponseEntity<CurrentUserDto> changePassword(
            @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest http, HttpServletResponse response) {
        return delegate.changePassword(request, http, response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http, HttpServletResponse response) {
        return delegate.logout(http, response);
    }
}
