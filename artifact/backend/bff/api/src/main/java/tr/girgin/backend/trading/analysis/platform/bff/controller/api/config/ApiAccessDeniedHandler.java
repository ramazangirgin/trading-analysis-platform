package tr.girgin.backend.trading.analysis.platform.bff.controller.api.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

/**
 * 403 with an error body: a missing or wrong CSRF token is {@code invalid_csrf_token}, anything else
 * {@code forbidden}.
 */
final class ApiAccessDeniedHandler implements AccessDeniedHandler {

    static final String INVALID_CSRF_TOKEN = "invalid_csrf_token";
    static final String FORBIDDEN = "forbidden";

    private final ApiErrorWriter writer;

    ApiAccessDeniedHandler(ApiErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        if (accessDeniedException instanceof CsrfException) {
            writer.write(response, HttpStatus.FORBIDDEN, INVALID_CSRF_TOKEN, "Missing or invalid CSRF token");
        } else {
            writer.write(response, HttpStatus.FORBIDDEN, FORBIDDEN, "Access denied");
        }
    }
}
