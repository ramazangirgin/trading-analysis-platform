package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.CurrentUserDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.LoginRequest;

public interface AuthApiDelegate {

    ResponseEntity<CurrentUserDto> login(LoginRequest request, HttpServletRequest http, HttpServletResponse response);

    ResponseEntity<CurrentUserDto> me(HttpServletRequest http, HttpServletResponse response);

    ResponseEntity<Void> logout(HttpServletRequest http, HttpServletResponse response);
}
