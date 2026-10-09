package tr.girgin.backend.trading.analysis.platform.library.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A {@link Clock} at a fixed instant that a test moves with {@link #set} and {@link #advance}. */
public final class MutableTestClock extends Clock {

    /** The instant a new clock starts at. */
    public static final Instant START = Instant.parse("2026-01-15T10:00:00Z");

    private volatile Instant now = START;

    public void set(Instant instant) {
        now = instant;
    }

    public void advance(Duration duration) {
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
