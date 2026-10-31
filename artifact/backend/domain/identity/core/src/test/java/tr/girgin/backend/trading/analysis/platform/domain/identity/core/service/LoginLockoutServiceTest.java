package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;

class LoginLockoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    private final LoginLockoutService service = new LoginLockoutService(5, Duration.ofMinutes(15));

    private static User user(int failedLogins, Optional<Instant> lockedUntil, Set<RoleId> roles) {
        return new User(
                new UserId(UUID.randomUUID().toString()),
                new Username("alice"),
                new PasswordHash("{noop}x"),
                true,
                false,
                failedLogins,
                lockedUntil,
                Optional.empty(),
                NOW,
                NOW,
                roles,
                3L);
    }

    @Test
    void aLockHoldsUntilItsInstantIsReached() {
        Instant until = NOW.plusSeconds(900);
        User locked = user(5, Optional.of(until), Set.of());

        assertThat(service.isLocked(user(0, Optional.empty(), Set.of()), NOW)).isFalse();
        assertThat(service.isLocked(locked, until.minusMillis(1))).isTrue();
        assertThat(service.isLocked(locked, until)).isFalse();
    }

    @Test
    void unlockedClearsTheCountAndTheLockAndKeepsTheRest() {
        Set<RoleId> roles = Set.of(new RoleId(UUID.randomUUID().toString()));
        User user = user(2, Optional.of(NOW.plusSeconds(900)), roles);

        assertThat(service.unlocked(user))
                .isEqualTo(new User(
                        user.id(),
                        user.username(),
                        user.passwordHash(),
                        true,
                        false,
                        0,
                        Optional.empty(),
                        Optional.empty(),
                        NOW,
                        NOW,
                        roles,
                        3L));
    }

    @Test
    void holdsTheConfiguredValues() {
        LoginLockoutService policy = new LoginLockoutService(5, Duration.ofMinutes(15));

        assertThat(policy.maxFailedAttempts()).isEqualTo(5);
        assertThat(policy.lockoutDuration()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void theBoundsOfTheAttemptsAreAccepted() {
        assertThatCode(() -> new LoginLockoutService(1, Duration.ofMinutes(15))).doesNotThrowAnyException();
        assertThatCode(() -> new LoginLockoutService(100, Duration.ofMinutes(15)))
                .doesNotThrowAnyException();
    }

    @Test
    void attemptsOutsideTheBoundsFailAndNameTheProperty() {
        assertThatThrownBy(() -> new LoginLockoutService(0, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform.login.max-failed-attempts");
        assertThatThrownBy(() -> new LoginLockoutService(101, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform.login.max-failed-attempts");
    }

    @Test
    void theBoundsOfTheDurationAreAccepted() {
        assertThatCode(() -> new LoginLockoutService(5, Duration.ofMinutes(1))).doesNotThrowAnyException();
        assertThatCode(() -> new LoginLockoutService(5, Duration.ofHours(24))).doesNotThrowAnyException();
    }

    @Test
    void aDurationOutsideTheBoundsFailsAndNamesTheProperty() {
        assertThatThrownBy(() -> new LoginLockoutService(5, Duration.ofSeconds(59)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform.login.lockout-duration");
        assertThatThrownBy(() -> new LoginLockoutService(5, Duration.ofHours(24).plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform.login.lockout-duration");
    }
}
