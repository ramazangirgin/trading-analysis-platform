package tr.girgin.backend.trading.analysis.platform.domain.report.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DataDirWatchServiceTest {

    private static final Duration QUIET = Duration.ofMillis(200);

    private Runnable change;
    private boolean closed;
    private final AtomicInteger settled = new AtomicInteger();

    private final DataDirWatchService service = new DataDirWatchService(
            onChange -> {
                change = onChange;
                return () -> closed = true;
            },
            QUIET,
            Duration.ofSeconds(1),
            System::nanoTime);

    @Test
    void aBurstOfChangesSettlesOnce() throws Exception {
        try (AutoCloseable _ = service.watch(settled::incrementAndGet)) {
            for (int i = 0; i < 5; i++) {
                change.run();
            }
            Thread.sleep(QUIET.multipliedBy(3));

            assertThat(settled).hasValue(1);

            change.run();
            Thread.sleep(QUIET.multipliedBy(3));

            assertThat(settled).hasValue(2);
        }
        assertThat(closed).isTrue();
    }

    @Test
    void changesThatNeverStopStillSettleAfterTheMaximumDelay() throws Exception {
        DataDirWatchService impatient = new DataDirWatchService(
                onChange -> {
                    change = onChange;
                    return () -> {};
                },
                QUIET,
                Duration.ofMillis(500),
                System::nanoTime);

        try (AutoCloseable _ = impatient.watch(settled::incrementAndGet)) {
            // A change every 50 ms never leaves the quiet period, like runs.json during a run.
            long end = System.nanoTime() + Duration.ofMillis(1_500).toNanos();
            while (System.nanoTime() < end) {
                change.run();
                Thread.sleep(50);
            }

            assertThat(settled.get()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void nothingSettlesAfterClosing() throws Exception {
        service.watch(settled::incrementAndGet).close();

        change.run();
        Thread.sleep(QUIET.multipliedBy(2));

        assertThat(settled).hasValue(0);
    }
}
