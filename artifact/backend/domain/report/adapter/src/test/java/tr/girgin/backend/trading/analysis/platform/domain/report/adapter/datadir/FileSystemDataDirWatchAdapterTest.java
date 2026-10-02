package tr.girgin.backend.trading.analysis.platform.domain.report.adapter.datadir;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real file system events; on macOS the JDK polls, so each change takes a few seconds to arrive. */
class FileSystemDataDirWatchAdapterTest {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @TempDir
    private Path dataDir;

    private final Semaphore changes = new Semaphore(0);

    @Test
    void noticesNewRunsInTheResultsTreeAndTheRunHistory() throws Exception {
        Path logs = Files.createDirectories(dataDir.resolve("logs"));
        FileSystemDataDirWatchAdapter adapter =
                new FileSystemDataDirWatchAdapter(dataDir, logs, dataDir.resolve("reports"));

        try (AutoCloseable _ = adapter.watch(changes::release)) {
            // A new ticker and date: the directories appear first, then the files in them.
            Path stage = Files.createDirectories(logs.resolve("AMD/2026-09-30/reports/1_analysts"));
            assertChanged();

            drain();
            Files.writeString(stage.resolve("market.md"), "# AMD market");
            assertChanged();

            drain();
            Files.writeString(dataDir.resolve("runs.json"), "{}");
            assertChanged();
        }
    }

    @Test
    void ignoresFilesTheImportDoesNotRead() throws Exception {
        Path cache = Files.createDirectories(dataDir.resolve("cache"));
        FileSystemDataDirWatchAdapter adapter =
                new FileSystemDataDirWatchAdapter(dataDir, dataDir.resolve("logs"), dataDir.resolve("reports"));

        try (AutoCloseable _ = adapter.watch(changes::release)) {
            Files.writeString(cache.resolve("AMD-YFin-data.csv"), "Date,Close");
            Files.writeString(dataDir.resolve("ui_state.json"), "{}");

            assertThat(changes.tryAcquire(6, TimeUnit.SECONDS)).isFalse();
        }
    }

    private void assertChanged() throws InterruptedException {
        assertThat(changes.tryAcquire(WAIT.toSeconds(), TimeUnit.SECONDS))
                .as("change noticed")
                .isTrue();
    }

    private void drain() {
        changes.drainPermits();
    }
}
