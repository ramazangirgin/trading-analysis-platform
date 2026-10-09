package tr.girgin.backend.trading.analysis.platform.library.persistence;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import org.springframework.data.auditing.DateTimeProvider;

/**
 * The time of JPA auditing, from the application {@link Clock}. Truncated to microseconds, which is
 * what PostgreSQL's {@code TIMESTAMPTZ} keeps, so the value a save returns equals the one read back.
 */
public final class ClockDateTimeProvider implements DateTimeProvider {

    private final Clock clock;

    public ClockDateTimeProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<TemporalAccessor> getNow() {
        return Optional.of(clock.instant().truncatedTo(ChronoUnit.MICROS));
    }
}
