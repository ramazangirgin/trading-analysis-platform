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
 * Limits the requests of a client address to the routes open to visitors ({@code GET /api/public/**}): over the
 * limit the answer is 429 {@code too_many_requests} with {@code Retry-After}, and the request never reaches
 * the route. The client address is {@link HttpServletRequest#getRemoteAddr()}, as for the
 * {@link LoginRateLimitFilter}.
 */
final class PublicRateLimitFilter extends OncePerRequestFilter {

    static final String ERROR_CODE = "too_many_requests";

    private static final String PUBLIC_PATH = "/api/public/";

    private final PublicRequestLimiter limiter;
    private final ApiErrorWriter writer;

    PublicRateLimitFilter(PublicRequestLimiter limiter, ApiErrorWriter writer) {
        this.limiter = limiter;
        this.writer = writer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.GET.matches(request.getMethod())
                || !request.getServletPath().startsWith(PUBLIC_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Duration> blocked = limiter.tryAcquire(request.getRemoteAddr());
        if (blocked.isPresent()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds(blocked.get())));
            writer.write(
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    ERROR_CODE,
                    "Too many requests from this address, try again later");
            return;
        }
        chain.doFilter(request, response);
    }

    private static long seconds(Duration duration) {
        long seconds = duration.getSeconds() + (duration.getNano() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }
}
