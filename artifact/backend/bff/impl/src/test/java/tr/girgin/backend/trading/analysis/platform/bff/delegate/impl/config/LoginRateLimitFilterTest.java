package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class LoginRateLimitFilterTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ADDRESS = "203.0.113.7";

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock, 2, Duration.ofMinutes(5));
    private final LoginRateLimitFilter filter = new LoginRateLimitFilter(limiter, new ApiErrorWriter(JSON));
    private final AtomicInteger chainCalls = new AtomicInteger();

    private MockHttpServletResponse run(String method, String path, int answer) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr(ADDRESS);
        // A proxy header the filter must not trust.
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (_, res) -> {
            chainCalls.incrementAndGet();
            ((MockHttpServletResponse) res).setStatus(answer);
        };
        filter.doFilter(request, response, chain);
        return response;
    }

    private MockHttpServletResponse login(int answer) throws Exception {
        return run("POST", "/api/auth/login", answer);
    }

    @Test
    void aBlockedAddressGets429WithTheErrorBodyAndRetryAfterAndTheLoginNeverRuns() throws Exception {
        login(401);
        login(401);
        assertThat(chainCalls).hasValue(2);

        MockHttpServletResponse blocked = login(200);

        assertThat(chainCalls).hasValue(2);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("300");
        JsonNode body = JSON.readTree(blocked.getContentAsString());
        assertThat(body.get("errorCode").asString()).isEqualTo("too_many_login_attempts");
    }

    @Test
    void aWrongPasswordIsRecordedButASuccessOrAForbiddenAnswerIsNot() throws Exception {
        login(200);
        login(403);
        login(200);
        login(403);

        assertThat(limiter.blockedFor(ADDRESS)).isEmpty();

        login(401);
        login(401);

        assertThat(limiter.blockedFor(ADDRESS)).isPresent();
    }

    @Test
    void otherPathsAndMethodsPassUntouched() throws Exception {
        limiter.recordFailure(ADDRESS);
        limiter.recordFailure(ADDRESS);

        assertThat(run("GET", "/api/auth/login", 200).getStatus()).isEqualTo(200);
        assertThat(run("POST", "/api/auth/logout", 204).getStatus()).isEqualTo(204);
        assertThat(run("POST", "/api/presets", 401).getStatus()).isEqualTo(401);
        assertThat(chainCalls).hasValue(3);
        // The 401 of another path was not counted either: a blocked address stays at its two failures.
        assertThat(limiter.blockedFor(ADDRESS)).isPresent();
    }
}
