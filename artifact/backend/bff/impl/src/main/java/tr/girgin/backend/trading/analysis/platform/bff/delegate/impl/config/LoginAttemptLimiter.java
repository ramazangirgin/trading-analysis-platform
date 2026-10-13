package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A sliding window of the failed logins of each client address, in memory like the sessions (a restart clears
 * it). An address that has failed {@code ip-max-failed-attempts} times (20 by default, 1 to 10,000) within
 * {@code ip-window} (5 minutes by default, 1 minute to 24 hours) is blocked until the oldest failure leaves
 * the window. Only failures count, so a busy but honest host is not slowed down.
 *
 * <p>At most {@value #MAX_TRACKED_ADDRESSES} addresses are tracked: when the table is full, addresses without
 * a failure in the window are dropped first, then the one whose latest failure is the oldest.
 */
@Component
class LoginAttemptLimiter {

    static final int MAX_TRACKED_ADDRESSES = 10_000;
    static final int LOWEST_MAX_FAILED_ATTEMPTS = 1;
    static final int HIGHEST_MAX_FAILED_ATTEMPTS = 10_000;
    static final Duration LOWEST_WINDOW = Duration.ofMinutes(1);
    static final Duration HIGHEST_WINDOW = Duration.ofHours(24);

    private final Clock clock;
    private final int maxFailedAttempts;
    private final Duration window;
    // Every access goes through compute…, which guards the deque of its address.
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Object evictionLock = new Object();

    LoginAttemptLimiter(
            Clock clock,
            @Value("${platform.login.ip-max-failed-attempts:20}") int maxFailedAttempts,
            @Value("${platform.login.ip-window:5m}") Duration window) {
        if (maxFailedAttempts < LOWEST_MAX_FAILED_ATTEMPTS || maxFailedAttempts > HIGHEST_MAX_FAILED_ATTEMPTS) {
            throw new IllegalArgumentException("platform.login.ip-max-failed-attempts must be between "
                    + LOWEST_MAX_FAILED_ATTEMPTS + " and " + HIGHEST_MAX_FAILED_ATTEMPTS + " but is "
                    + maxFailedAttempts);
        }
        if (window.compareTo(LOWEST_WINDOW) < 0 || window.compareTo(HIGHEST_WINDOW) > 0) {
            throw new IllegalArgumentException("platform.login.ip-window must be between " + LOWEST_WINDOW + " and "
                    + HIGHEST_WINDOW + " but is " + window);
        }
        this.clock = clock;
        this.maxFailedAttempts = maxFailedAttempts;
        this.window = window;
    }

    /** The time until the address may try again; empty while it is under the limit. */
    Optional<Duration> blockedFor(String address) {
        AtomicReference<Duration> blocked = new AtomicReference<>();
        Instant now = clock.instant();
        failures.computeIfPresent(address, (_, times) -> {
            prune(times, now);
            if (times.size() >= maxFailedAttempts) {
                blocked.set(Duration.between(now, times.peekFirst().plus(window)));
            }
            return times.isEmpty() ? null : times;
        });
        return Optional.ofNullable(blocked.get());
    }

    void recordFailure(String address) {
        if (failures.size() >= MAX_TRACKED_ADDRESSES && !failures.containsKey(address)) {
            makeRoom();
        }
        Instant now = clock.instant();
        failures.compute(address, (_, times) -> {
            Deque<Instant> kept = times == null ? new ArrayDeque<>() : times;
            prune(kept, now);
            kept.addLast(now);
            return kept;
        });
    }

    /** How many addresses are tracked now, for the tests of the memory bound. */
    int trackedAddresses() {
        return failures.size();
    }

    boolean isTracking(String address) {
        return failures.containsKey(address);
    }

    private void prune(Deque<Instant> times, Instant now) {
        Instant cutoff = now.minus(window);
        while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
            times.removeFirst();
        }
    }

    private void makeRoom() {
        synchronized (evictionLock) {
            Instant now = clock.instant();
            for (String address : failures.keySet()) {
                failures.computeIfPresent(address, (_, times) -> {
                    prune(times, now);
                    return times.isEmpty() ? null : times;
                });
            }
            if (failures.size() < MAX_TRACKED_ADDRESSES) {
                return;
            }
            String oldest = null;
            Instant oldestFailure = null;
            for (String address : failures.keySet()) {
                Instant latest = latestFailure(address);
                if (latest != null && (oldestFailure == null || latest.isBefore(oldestFailure))) {
                    oldest = address;
                    oldestFailure = latest;
                }
            }
            if (oldest != null) {
                failures.remove(oldest);
            }
        }
    }

    private Instant latestFailure(String address) {
        AtomicReference<Instant> latest = new AtomicReference<>();
        failures.computeIfPresent(address, (_, times) -> {
            latest.set(times.peekLast());
            return times;
        });
        return latest.get();
    }
}
