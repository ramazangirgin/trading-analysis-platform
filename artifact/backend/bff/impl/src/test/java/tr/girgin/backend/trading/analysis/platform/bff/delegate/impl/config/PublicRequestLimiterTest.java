package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PublicRequestLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String ADDRESS = "10.0.0.1";

    private final MutableClock clock = new MutableClock();
    private final PublicRequestLimiter limiter = new PublicRequestLimiter(clock, 3, WINDOW);

    @Test
    void theRequestsOverTheLimitAreRefusedAndNotCounted() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire(ADDRESS)).isEmpty();
        }

        assertThat(limiter.tryAcquire(ADDRESS)).contains(WINDOW);

        clock.advance(Duration.ofSeconds(20));
        // The refused request did not extend the block.
        assertThat(limiter.tryAcquire(ADDRESS)).contains(Duration.ofSeconds(40));
    }

    @Test
    void theWindowSlidesAndFreesOneSlotAtATime() {
        limiter.tryAcquire(ADDRESS);
        clock.advance(Duration.ofSeconds(30));
        limiter.tryAcquire(ADDRESS);
        limiter.tryAcquire(ADDRESS);

        assertThat(limiter.tryAcquire(ADDRESS)).contains(Duration.ofSeconds(30));

        clock.advance(Duration.ofSeconds(30));

        assertThat(limiter.tryAcquire(ADDRESS)).isEmpty();
        assertThat(limiter.tryAcquire(ADDRESS)).isPresent();
    }

    @Test
    void twoAddressesAreCountedApart() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(ADDRESS);
        }

        assertThat(limiter.tryAcquire(ADDRESS)).isPresent();
        assertThat(limiter.tryAcquire("10.0.0.2")).isEmpty();
    }

    @Test
    void atTheMemoryBoundExpiredAddressesGoFirst() {
        limiter.tryAcquire("old");
        clock.advance(WINDOW.plusSeconds(1));
        for (int i = 1; i < PublicRequestLimiter.MAX_TRACKED_ADDRESSES; i++) {
            limiter.tryAcquire("address-" + i);
        }

        limiter.tryAcquire("newcomer");

        assertThat(limiter.trackedAddresses()).isEqualTo(PublicRequestLimiter.MAX_TRACKED_ADDRESSES);
        assertThat(limiter.isTracking("old")).isFalse();
        assertThat(limiter.isTracking("newcomer")).isTrue();
        assertThat(limiter.isTracking("address-1")).isTrue();
    }

    @Test
    void atTheMemoryBoundWithNoExpiredAddressTheOneWithTheOldestRequestGoes() {
        limiter.tryAcquire("oldest");
        clock.advance(Duration.ofSeconds(1));
        for (int i = 1; i < PublicRequestLimiter.MAX_TRACKED_ADDRESSES; i++) {
            limiter.tryAcquire("address-" + i);
        }

        limiter.tryAcquire("newcomer");

        assertThat(limiter.trackedAddresses()).isEqualTo(PublicRequestLimiter.MAX_TRACKED_ADDRESSES);
        assertThat(limiter.isTracking("oldest")).isFalse();
        assertThat(limiter.isTracking("newcomer")).isTrue();
    }

    @Test
    void theSettingsAreCheckedAtStartup() {
        assertThatThrownBy(() -> new PublicRequestLimiter(clock, 0, WINDOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform.public.rate-limit.max-requests");
        assertThatThrownBy(() -> new PublicRequestLimiter(clock, 10_001, WINDOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform.public.rate-limit.max-requests");
        assertThatThrownBy(() -> new PublicRequestLimiter(clock, 60, Duration.ofMillis(999)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform.public.rate-limit.window");
        assertThatThrownBy(() ->
                        new PublicRequestLimiter(clock, 60, Duration.ofHours(24).plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform.public.rate-limit.window");
        new PublicRequestLimiter(clock, 1, Duration.ofSeconds(1));
        new PublicRequestLimiter(clock, 10_000, Duration.ofHours(24));
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
