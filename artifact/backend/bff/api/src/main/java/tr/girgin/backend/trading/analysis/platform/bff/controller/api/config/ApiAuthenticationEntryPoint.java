package tr.girgin.backend.trading.analysis.platform.bff.controller.api.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** A request without a session gets 401 with an error body, never a redirect to a login page. */
final class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String ERROR_CODE = "unauthorized";

    private final ApiErrorWriter writer;

    ApiAuthenticationEntryPoint(ApiErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        writer.write(response, HttpStatus.UNAUTHORIZED, ERROR_CODE, "Authentication required");
    }
}
