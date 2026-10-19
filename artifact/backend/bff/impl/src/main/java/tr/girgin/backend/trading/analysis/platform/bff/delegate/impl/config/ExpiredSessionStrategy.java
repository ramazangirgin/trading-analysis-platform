package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;

/**
 * Answers the first request of a session that was ended (a disable, a password reset, a role change). The
 * concurrency filter has logged the session out already. An API request gets 401 {@code unauthorized}; any other
 * request is sent to the same URL again, which then arrives without a session and gets the static UI, so a page
 * or script of the SPA still loads and the SPA itself goes to the login page.
 */
final class ExpiredSessionStrategy implements SessionInformationExpiredStrategy {

    private static final String API_PREFIX = "/api/";

    private final ApiErrorWriter writer;

    ExpiredSessionStrategy(ApiErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public void onExpiredSessionDetected(SessionInformationExpiredEvent event) throws IOException {
        HttpServletRequest request = event.getRequest();
        HttpServletResponse response = event.getResponse();
        if (request.getServletPath().startsWith(API_PREFIX)) {
            writer.write(response, HttpStatus.UNAUTHORIZED, ApiAuthenticationEntryPoint.ERROR_CODE, "Session ended");
            return;
        }
        String query = request.getQueryString();
        response.sendRedirect(request.getRequestURI() + (query == null ? "" : "?" + query));
    }
}
