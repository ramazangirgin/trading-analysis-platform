package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock, 3, WINDOW);

    @Test
    void underTheLimitNothingIsBlockedAndTheLastAllowedFailureBlocks() {
        limiter.tryAcquire("10.0.0.1");
        limiter.tryAcquire("10.0.0.1");

        assertThat(limiter.blockedFor("10.0.0.1")).isEmpty();

        limiter.tryAcquire("10.0.0.1");

        assertThat(limiter.blockedFor("10.0.0.1")).isPresent();
    }

    @Test
    void theAttemptsOverTheLimitAreRefusedAndNotCounted() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("10.0.0.1")).isEmpty();
        }

        assertThat(limiter.tryAcquire("10.0.0.1")).contains(WINDOW);

        clock.advance(Duration.ofMinutes(1));
        // The refused attempt did not extend the block.
        assertThat(limiter.tryAcquire("10.0.0.1")).contains(Duration.ofMinutes(4));
    }

    @Test
    void aReleasedAttemptFreesItsSlot() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("10.0.0.1");
        }
        assertThat(limiter.tryAcquire("10.0.0.1")).isPresent();

        limiter.release("10.0.0.1");

        assertThat(limiter.tryAcquire("10.0.0.1")).isEmpty();
    }

    @Test
    void releasingTheOnlyAttemptStopsTrackingTheAddress() {
        limiter.tryAcquire("10.0.0.1");

        limiter.release("10.0.0.1");

        assertThat(limiter.isTracking("10.0.0.1")).isFalse();
    }

    @Test
    void concurrentAttemptsLetExactlyTheLimitThrough() throws Exception {
        int attempts = 50;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return limiter.tryAcquire("10.0.0.1").isEmpty();
                }));
            }
            start.countDown();
            int allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    allowed++;
                }
            }

            assertThat(allowed).isEqualTo(3);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void blockedForIsTheTimeUntilTheOldestFailureLeavesTheWindow() {
        limiter.tryAcquire("10.0.0.1");
        clock.advance(Duration.ofMinutes(1));
        limiter.tryAcquire("10.0.0.1");
        limiter.tryAcquire("10.0.0.1");

        assertThat(limiter.blockedFor("10.0.0.1")).contains(Duration.ofMinutes(4));

        clock.advance(Duration.ofMinutes(4).minusSeconds(1));
        assertThat(limiter.blockedFor("10.0.0.1")).contains(Duration.ofSeconds(1));

        clock.advance(Duration.ofSeconds(1));
        // Only two failures are left in the window.
        assertThat(limiter.blockedFor("10.0.0.1")).isEmpty();
    }

    @Test
    void twoAddressesAreCountedApart() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("10.0.0.1");
        }

        assertThat(limiter.blockedFor("10.0.0.1")).isPresent();
        assertThat(limiter.blockedFor("10.0.0.2")).isEmpty();
    }

    @Test
    void atTheMemoryBoundExpiredAddressesGoFirst() {
        limiter.tryAcquire("old");
        clock.advance(WINDOW.plusSeconds(1));
        for (int i = 1; i < LoginAttemptLimiter.MAX_TRACKED_ADDRESSES; i++) {
            limiter.tryAcquire("address-" + i);
        }

        assertThat(limiter.trackedAddresses()).isEqualTo(LoginAttemptLimiter.MAX_TRACKED_ADDRESSES);

        limiter.tryAcquire("newcomer");

        assertThat(limiter.trackedAddresses()).isEqualTo(LoginAttemptLimiter.MAX_TRACKED_ADDRESSES);
        assertThat(limiter.isTracking("old")).isFalse();
        assertThat(limiter.isTracking("newcomer")).isTrue();
        assertThat(limiter.isTracking("address-1")).isTrue();
    }

    @Test
    void atTheMemoryBoundWithNoExpiredAddressTheOneWithTheOldestFailureGoes() {
        limiter.tryAcquire("oldest");
        clock.advance(Duration.ofSeconds(1));
        for (int i = 1; i < LoginAttemptLimiter.MAX_TRACKED_ADDRESSES; i++) {
            limiter.tryAcquire("address-" + i);
        }

        limiter.tryAcquire("newcomer");

        assertThat(limiter.trackedAddresses()).isEqualTo(LoginAttemptLimiter.MAX_TRACKED_ADDRESSES);
        assertThat(limiter.isTracking("oldest")).isFalse();
        assertThat(limiter.isTracking("newcomer")).isTrue();
        assertThat(limiter.isTracking("address-1")).isTrue();
    }

    @Test
    void theSettingsAreCheckedAtStartup() {
        assertThatThrownBy(() -> new LoginAttemptLimiter(clock, 0, WINDOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform.login.ip-max-failed-attempts");
        assertThatThrownBy(() -> new LoginAttemptLimiter(clock, 10_001, WINDOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform.login.ip-max-failed-attempts");
        assertThatThrownBy(() -> new LoginAttemptLimiter(clock, 20, Duration.ofSeconds(59)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform.login.ip-window");
        assertThatThrownBy(() ->
                        new LoginAttemptLimiter(clock, 20, Duration.ofHours(24).plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform.login.ip-window");
        new LoginAttemptLimiter(clock, 1, Duration.ofMinutes(1));
        new LoginAttemptLimiter(clock, 10_000, Duration.ofHours(24));
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
