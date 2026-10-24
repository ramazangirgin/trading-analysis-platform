package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PublicRateLimitFilterTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ADDRESS = "203.0.113.7";
    private static final String LIST = "/api/public/analyses";

    private final MutableClock clock = new MutableClock();
    private final PublicRequestLimiter limiter = new PublicRequestLimiter(clock, 2, Duration.ofMinutes(1));
    private final PublicRateLimitFilter filter = new PublicRateLimitFilter(limiter, new ApiErrorWriter(JSON));
    private final AtomicInteger chainCalls = new AtomicInteger();

    private MockHttpServletResponse run(String address, String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr(address);
        // A proxy header the filter must not trust.
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (_, res) -> {
            chainCalls.incrementAndGet();
            ((MockHttpServletResponse) res).setStatus(200);
        };
        filter.doFilter(request, response, chain);
        return response;
    }

    private MockHttpServletResponse get(String path) throws Exception {
        return run(ADDRESS, "GET", path);
    }

    @Test
    void theRequestOverTheLimitGets429WithTheErrorBodyAndRetryAfterAndNeverReachesTheRoute() throws Exception {
        assertThat(get(LIST).getStatus()).isEqualTo(200);
        assertThat(get(LIST + "/abc").getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = get(LIST);

        assertThat(chainCalls).hasValue(2);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        JsonNode body = JSON.readTree(blocked.getContentAsString());
        assertThat(body.get("errorCode").asString()).isEqualTo("too_many_requests");
    }

    @Test
    void theWindowFreesUp() throws Exception {
        get(LIST);
        get(LIST);
        assertThat(get(LIST).getStatus()).isEqualTo(429);

        clock.advance(Duration.ofSeconds(61));

        assertThat(get(LIST).getStatus()).isEqualTo(200);
    }

    @Test
    void addressesAreIndependent() throws Exception {
        get(LIST);
        get(LIST);

        assertThat(get(LIST).getStatus()).isEqualTo(429);
        assertThat(run("203.0.113.8", "GET", LIST).getStatus()).isEqualTo(200);
    }

    @Test
    void otherPathsAndMethodsAreNotCounted() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(get("/api/analyses").getStatus()).isEqualTo(200);
            assertThat(get("/api/publication").getStatus()).isEqualTo(200);
            assertThat(run(ADDRESS, "POST", LIST).getStatus()).isEqualTo(200);
        }

        assertThat(chainCalls).hasValue(15);
        assertThat(limiter.isTracking(ADDRESS)).isFalse();
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-15T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
