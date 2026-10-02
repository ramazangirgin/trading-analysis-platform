package tr.girgin.backend.trading.analysis.platform.domain.report.adapter.datadir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.outbound.datadir.DataDirWatchPort;

/**
 * Watches what {@link FileSystemDataDirAdapter} and the run history adapter read: the results
 * tree (down to {@code <T>/<D>/reports/<stage>/}), {@code runs.json} and the deep reports. Not the
 * price cache or the memory log, which change all the time and are not imported.
 *
 * <p>On macOS the JDK's watch service polls, so a change is noticed after a few seconds.
 */
@Component
class FileSystemDataDirWatchAdapter implements DataDirWatchPort {

    private static final Logger log = LoggerFactory.getLogger(FileSystemDataDirWatchAdapter.class);
    // results/<TICKER>/<DATE>/reports/<stage>: the deepest directory holding report files.
    private static final int RESULTS_DEPTH = 4;
    private static final String RUN_HISTORY = "runs.json";

    private final Path dataDir;
    private final Path resultsDir;
    private final Path deepReportsDir;

    FileSystemDataDirWatchAdapter(
            @Value("${platform.data-dir}") Path dataDir,
            @Value("${platform.results-dir}") Path resultsDir,
            @Value("${platform.data-dir}/reports") Path deepReportsDir) {
        this.dataDir = dataDir.toAbsolutePath().normalize();
        this.resultsDir = resultsDir.toAbsolutePath().normalize();
        this.deepReportsDir = deepReportsDir.toAbsolutePath().normalize();
    }

    @Override
    public AutoCloseable watch(Runnable onChange) {
        WatchService service;
        try {
            service = dataDir.getFileSystem().newWatchService();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot watch " + dataDir, e);
        }
        Watch watch = new Watch(service, onChange);
        watch.register(dataDir);
        watch.register(deepReportsDir);
        watch.registerTree(resultsDir);
        Thread.ofVirtual().name("data-dir-watch").start(watch::run);
        log.info("Watching {} for new runs", dataDir);
        return service;
    }

    private final class Watch {

        private final WatchService service;
        private final Runnable onChange;
        private final Map<WatchKey, Path> dirs = new ConcurrentHashMap<>();

        Watch(WatchService service, Runnable onChange) {
            this.service = service;
            this.onChange = onChange;
        }

        @SuppressWarnings("checkstyle:IllegalCatch") // watch thread: log the failure, do not lose it
        void run() {
            try {
                while (true) {
                    WatchKey key = service.take();
                    Path dir = dirs.get(key);
                    boolean relevant = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        relevant |= handle(dir, event);
                    }
                    if (!key.reset()) {
                        dirs.remove(key);
                    }
                    if (relevant) {
                        onChange.run();
                    }
                }
            } catch (ClosedWatchServiceException | InterruptedException _) {
                log.debug("Stopped watching {}", dataDir);
            } catch (RuntimeException e) {
                log.error("Watching {} failed; use rescan to pick up new runs", dataDir, e);
            }
        }

        private boolean handle(Path dir, WatchEvent<?> event) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW || dir == null) {
                return true;
            }
            Path path = dir.resolve((Path) event.context());
            boolean created = event.kind() == StandardWatchEventKinds.ENTRY_CREATE;
            if (dir.equals(dataDir)) {
                return handleDataDirEntry(path, created);
            }
            if (created && path.startsWith(resultsDir) && Files.isDirectory(path)) {
                registerTree(path);
            }
            return created
                    || event.kind() == StandardWatchEventKinds.ENTRY_MODIFY
                    || event.kind() == StandardWatchEventKinds.ENTRY_DELETE;
        }

        /** The data dir itself: only the run history, and the two directories appearing. */
        private boolean handleDataDirEntry(Path path, boolean created) {
            if (created && path.equals(deepReportsDir)) {
                register(path);
            } else if (created && path.equals(resultsDir)) {
                registerTree(path);
            }
            return path.getFileName().toString().equals(RUN_HISTORY)
                    || path.equals(deepReportsDir)
                    || path.equals(resultsDir);
        }

        void registerTree(Path root) {
            if (!Files.isDirectory(root)) {
                return;
            }
            int level =
                    root.equals(resultsDir) ? 0 : resultsDir.relativize(root).getNameCount();
            if (level > RESULTS_DEPTH) {
                return;
            }
            try (Stream<Path> tree = Files.walk(root, RESULTS_DEPTH - level)) {
                tree.filter(Files::isDirectory).forEach(this::register);
            } catch (IOException | UncheckedIOException e) {
                log.warn("Cannot watch all of {}: {}", root, e.getMessage());
            }
        }

        void register(Path dir) {
            if (!Files.isDirectory(dir)) {
                return;
            }
            try {
                dirs.put(
                        dir.register(
                                service,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY,
                                StandardWatchEventKinds.ENTRY_DELETE),
                        dir);
            } catch (ClosedWatchServiceException _) {
                // Closed meanwhile.
            } catch (IOException e) {
                log.warn("Cannot watch {}: {}", dir, e.getMessage());
            }
        }
    }
}
