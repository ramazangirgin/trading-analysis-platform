package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LoginLockoutServiceTest {

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
