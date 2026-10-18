package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits the failed logins of a client address: while the address is blocked, {@code POST /api/auth/login}
 * answers 429 {@code too_many_login_attempts} with {@code Retry-After} and never reaches the login. A login
 * holds a slot of its address while it runs and keeps it only when it is answered 401 (wrong password,
 * unknown user, locked account), which counts as a failure; any other answer gives the slot back. So more
 * logins than the limit running at once from one address get 429 too.
 *
 * <p>The client address is {@link HttpServletRequest#getRemoteAddr()}. Behind a reverse proxy on an internal
 * address (the Caddy overlay), Tomcat replaces it with the {@code X-Forwarded-For} client
 * ({@code server.forward-headers-strategy=native}); from any other address {@code X-Forwarded-For} is ignored,
 * as any client can set it.
 */
final class LoginRateLimitFilter extends OncePerRequestFilter {

    static final String ERROR_CODE = "too_many_login_attempts";

    private static final String LOGIN_PATH = "/api/auth/login";

    private final LoginAttemptLimiter limiter;
    private final ApiErrorWriter writer;

    LoginRateLimitFilter(LoginAttemptLimiter limiter, ApiErrorWriter writer) {
        this.limiter = limiter;
        this.writer = writer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !LOGIN_PATH.equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String address = request.getRemoteAddr();
        // The attempt is counted before the login runs: a check followed by a count would let every login
        // in flight at the limit through.
        Optional<Duration> blocked = limiter.tryAcquire(address);
        if (blocked.isPresent()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds(blocked.get())));
            writer.write(
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    ERROR_CODE,
                    "Too many failed logins from this address, try again later");
            return;
        }
        boolean failed = false;
        try {
            chain.doFilter(request, response);
            failed = response.getStatus() == HttpStatus.UNAUTHORIZED.value();
        } finally {
            if (!failed) {
                limiter.release(address);
            }
        }
    }

    // Rounded up, and at least one second.
    private static long seconds(Duration duration) {
        long seconds = duration.getSeconds() + (duration.getNano() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }
}
