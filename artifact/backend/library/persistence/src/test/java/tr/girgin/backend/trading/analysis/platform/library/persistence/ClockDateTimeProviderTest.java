package tr.girgin.backend.trading.analysis.platform.library.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClockDateTimeProviderTest {

    @Test
    void returnsTheInstantOfTheClockTruncatedToMicroseconds() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-15T10:00:00.123456789Z"), ZoneOffset.UTC);

        assertThat(new ClockDateTimeProvider(clock).getNow()).contains(Instant.parse("2026-01-15T10:00:00.123456Z"));
    }

    @Test
    void followsTheClock() {
        MutableTestClock clock = new MutableTestClock();
        ClockDateTimeProvider provider = new ClockDateTimeProvider(clock);

        clock.set(Instant.parse("2026-02-01T00:00:00Z"));

        assertThat(provider.getNow()).contains(Instant.parse("2026-02-01T00:00:00Z"));
    }
}
