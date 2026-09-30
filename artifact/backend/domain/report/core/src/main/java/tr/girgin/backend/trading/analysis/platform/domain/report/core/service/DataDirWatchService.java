package tr.girgin.backend.trading.analysis.platform.domain.report.core.service;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.WatchDataDirUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.outbound.datadir.DataDirWatchPort;

/**
 * Turns the data dir's bursts of file changes into one signal each: a run writes a dozen report
 * files, and a third-party UI rewrites runs.json every few seconds while a run is going.
 */
@Service
class DataDirWatchService implements WatchDataDirUseCase {

    private static final Logger log = LoggerFactory.getLogger(DataDirWatchService.class);

    private final DataDirWatchPort watcher;
    private final Duration quietPeriod;
    private final Duration maxDelay;
    private final LongSupplier nanoTime;

    @Autowired
    DataDirWatchService(DataDirWatchPort watcher,
                        @Value("${platform.import.watch.quiet-period:5s}") Duration quietPeriod,
                        @Value("${platform.import.watch.max-delay:60s}") Duration maxDelay) {
        this(watcher, quietPeriod, maxDelay, System::nanoTime);
    }

    DataDirWatchService(DataDirWatchPort watcher, Duration quietPeriod, Duration maxDelay, LongSupplier nanoTime) {
        this.watcher = watcher;
        this.quietPeriod = quietPeriod;
        this.maxDelay = maxDelay;
        this.nanoTime = nanoTime;
    }

    @Override
    public AutoCloseable watch(Runnable onSettled) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("data-dir-settled").factory());
        Debouncer debouncer = new Debouncer(scheduler, onSettled);
        AutoCloseable watch = watcher.watch(debouncer::changed);
        return () -> {
            scheduler.shutdownNow();
            watch.close();
        };
    }

    /** Fires {@code quietPeriod} after the last change, but no later than {@code maxDelay} after the first. */
    private final class Debouncer {

        private final ScheduledExecutorService scheduler;
        private final Runnable onSettled;
        private ScheduledFuture<?> pending;
        private long firstChange;

        Debouncer(ScheduledExecutorService scheduler, Runnable onSettled) {
            this.scheduler = scheduler;
            this.onSettled = onSettled;
        }

        synchronized void changed() {
            long now = nanoTime.getAsLong();
            if (pending != null && !pending.isDone()) {
                if (now - firstChange >= maxDelay.toNanos()) {
                    return;
                }
                pending.cancel(false);
            } else {
                firstChange = now;
            }
            try {
                pending = scheduler.schedule(this::fire, quietPeriod.toNanos(), TimeUnit.NANOSECONDS);
            } catch (RejectedExecutionException e) {
                // Closed while a last change was coming in.
            }
        }

        private void fire() {
            try {
                onSettled.run();
            } catch (RuntimeException e) {
                log.error("Handling a data dir change failed", e);
            }
        }
    }
}
