package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * When an account is locked: after a number of wrong passwords in a row (5 by default, 1 to 100), for a
 * duration (15 minutes by default, 1 minute to 24 hours).
 */
@Service
class LoginLockoutService {

    static final int LOWEST_MAX_FAILED_ATTEMPTS = 1;
    static final int HIGHEST_MAX_FAILED_ATTEMPTS = 100;
    static final Duration LOWEST_LOCKOUT_DURATION = Duration.ofMinutes(1);
    static final Duration HIGHEST_LOCKOUT_DURATION = Duration.ofHours(24);

    private final int maxFailedAttempts;
    private final Duration lockoutDuration;

    LoginLockoutService(
            @Value("${platform.login.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${platform.login.lockout-duration:15m}") Duration lockoutDuration) {
        if (maxFailedAttempts < LOWEST_MAX_FAILED_ATTEMPTS || maxFailedAttempts > HIGHEST_MAX_FAILED_ATTEMPTS) {
            throw new IllegalArgumentException("platform.login.max-failed-attempts must be between "
                    + LOWEST_MAX_FAILED_ATTEMPTS + " and " + HIGHEST_MAX_FAILED_ATTEMPTS + " but is "
                    + maxFailedAttempts);
        }
        if (lockoutDuration.compareTo(LOWEST_LOCKOUT_DURATION) < 0
                || lockoutDuration.compareTo(HIGHEST_LOCKOUT_DURATION) > 0) {
            throw new IllegalArgumentException("platform.login.lockout-duration must be between "
                    + LOWEST_LOCKOUT_DURATION + " and " + HIGHEST_LOCKOUT_DURATION + " but is " + lockoutDuration);
        }
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutDuration = lockoutDuration;
    }

    int maxFailedAttempts() {
        return maxFailedAttempts;
    }

    Duration lockoutDuration() {
        return lockoutDuration;
    }
}
