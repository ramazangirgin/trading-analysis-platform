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
 * A sliding window of the requests of each client address to the routes open to visitors, in memory like the
 * sessions (a restart clears it). An address that has made {@code max-requests} requests (60 by default, 1 to
 * 10,000) within {@code window} (1 minute by default, 1 second to 24 hours) is blocked until the oldest request
 * leaves the window. A refused request is not counted, so it does not extend the block.
 *
 * <p>At most {@value #MAX_TRACKED_ADDRESSES} addresses are tracked: when the table is full, addresses without a
 * request in the window are dropped first, then the one whose latest request is the oldest.
 */
@Component
class PublicRequestLimiter {

    static final int MAX_TRACKED_ADDRESSES = 10_000;
    static final int LOWEST_MAX_REQUESTS = 1;
    static final int HIGHEST_MAX_REQUESTS = 10_000;
    static final Duration LOWEST_WINDOW = Duration.ofSeconds(1);
    static final Duration HIGHEST_WINDOW = Duration.ofHours(24);

    private final Clock clock;
    private final int maxRequests;
    private final Duration window;
    // Every access goes through compute…, which guards the deque of its address.
    private final Map<String, Deque<Instant>> requests = new ConcurrentHashMap<>();
    private final Object evictionLock = new Object();

    PublicRequestLimiter(
            Clock clock,
            @Value("${platform.public.rate-limit.max-requests:60}") int maxRequests,
            @Value("${platform.public.rate-limit.window:1m}") Duration window) {
        if (maxRequests < LOWEST_MAX_REQUESTS || maxRequests > HIGHEST_MAX_REQUESTS) {
            throw new IllegalArgumentException("platform.public.rate-limit.max-requests must be between "
                    + LOWEST_MAX_REQUESTS + " and " + HIGHEST_MAX_REQUESTS + " but is " + maxRequests);
        }
        if (window.compareTo(LOWEST_WINDOW) < 0 || window.compareTo(HIGHEST_WINDOW) > 0) {
            throw new IllegalArgumentException("platform.public.rate-limit.window must be between " + LOWEST_WINDOW
                    + " and " + HIGHEST_WINDOW + " but is " + window);
        }
        this.clock = clock;
        this.maxRequests = maxRequests;
        this.window = window;
    }

    /**
     * Checks the limit and counts the request in one atomic step. Returns the time until the address may
     * request again when it is over the limit (nothing is counted then); empty when the request may run.
     */
    Optional<Duration> tryAcquire(String address) {
        if (requests.size() >= MAX_TRACKED_ADDRESSES && !requests.containsKey(address)) {
            makeRoom();
        }
        AtomicReference<Duration> blocked = new AtomicReference<>();
        Instant now = clock.instant();
        requests.compute(address, (_, times) -> {
            Deque<Instant> kept = times == null ? new ArrayDeque<>() : times;
            prune(kept, now);
            if (kept.size() >= maxRequests) {
                blocked.set(Duration.between(now, kept.peekFirst().plus(window)));
            } else {
                kept.addLast(now);
            }
            return kept;
        });
        return Optional.ofNullable(blocked.get());
    }

    /** How many addresses are tracked now, for the tests of the memory bound. */
    int trackedAddresses() {
        return requests.size();
    }

    boolean isTracking(String address) {
        return requests.containsKey(address);
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
            for (String address : requests.keySet()) {
                requests.computeIfPresent(address, (_, times) -> {
                    prune(times, now);
                    return times.isEmpty() ? null : times;
                });
            }
            if (requests.size() < MAX_TRACKED_ADDRESSES) {
                return;
            }
            String oldest = null;
            Instant oldestRequest = null;
            for (String address : requests.keySet()) {
                Instant latest = latestRequest(address);
                if (latest != null && (oldestRequest == null || latest.isBefore(oldestRequest))) {
                    oldest = address;
                    oldestRequest = latest;
                }
            }
            if (oldest != null) {
                requests.remove(oldest);
            }
        }
    }

    private Instant latestRequest(String address) {
        AtomicReference<Instant> latest = new AtomicReference<>();
        requests.computeIfPresent(address, (_, times) -> {
            latest.set(times.peekLast());
            return times;
        });
        return latest.get();
    }
}
