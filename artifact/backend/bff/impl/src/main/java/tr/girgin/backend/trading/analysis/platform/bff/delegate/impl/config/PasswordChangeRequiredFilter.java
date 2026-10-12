package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Locks a session whose user must change their password: every {@code /api/**} request of it answers 403
 * {@code password_change_required}, except reading the user ({@code GET /api/auth/me}), changing the
 * password and logging out. The session is marked by the {@link #AUTHORITY} that the login adds.
 *
 * <p>The mark lives in the session and is not read from the database per request. The only ways to set
 * the flag on an existing user (an admin reset, #88) log that user's sessions out through the session
 * registry anyway, so a flagged user always logs in again and gets the mark.
 */
public final class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    /** The authority that marks a session of a user who must change their password. */
    public static final String AUTHORITY = "PASSWORD_CHANGE_REQUIRED";

    static final String ERROR_CODE = "password_change_required";

    private static final String API_PREFIX = "/api/";
    private static final Set<String> ALLOWED =
            Set.of("GET /api/auth/me", "POST /api/auth/password", "POST /api/auth/logout");

    private final ApiErrorWriter writer;

    PasswordChangeRequiredFilter(ApiErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getServletPath().startsWith(API_PREFIX)
                && isMarked(SecurityContextHolder.getContext().getAuthentication())
                && !ALLOWED.contains(request.getMethod() + " " + request.getServletPath())) {
            writer.write(response, HttpStatus.FORBIDDEN, ERROR_CODE, "The password must be changed first");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isMarked(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream().anyMatch(a -> AUTHORITY.equals(a.getAuthority()));
    }
}
