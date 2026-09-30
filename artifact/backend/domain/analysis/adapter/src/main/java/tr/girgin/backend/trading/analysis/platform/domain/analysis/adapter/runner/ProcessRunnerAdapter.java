package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.runner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline.RunnerOutputLineParser;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline.RunnerOutputLineToRunEventMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunEventSink;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunHandle;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunnerPort;

/**
 * Runs ta-runner as a local child process (PLAN.md section 4, option A): one process per run,
 * started with an argument list (never a shell), stdout read line by line on a virtual thread,
 * stderr kept as {@code run.log} in the run directory.
 *
 * <p>A run's handle is {@code <pid>@<process start, epoch ms>}: after a platform restart the pid
 * alone could by then belong to another process. A run found again that way is followed through
 * the {@code events.jsonl} it keeps writing, since its stdout went with the old platform.
 */
@Component
@Conditional(RunnerKind.Process.class)
class ProcessRunnerAdapter implements RunnerPort {

    private static final Logger log = LoggerFactory.getLogger(ProcessRunnerAdapter.class);

    private final AnalysisSpecToRunnerSpecMapper specMapper;
    private final RunnerOutputLineToRunEventMapper eventMapper;
    private final RunnerOutputLineParser parser = new RunnerOutputLineParser();
    private final JsonMapper specWriter = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();
    private final List<String> command;
    private final Path workingDir;
    private final Path runsDir;
    private final Path pricesFile;
    private final Duration stopGrace;
    private final Duration followInterval;
    private final Map<String, ProcessHandle> processes = new ConcurrentHashMap<>();

    ProcessRunnerAdapter(AnalysisSpecToRunnerSpecMapper specMapper,
                         RunnerOutputLineToRunEventMapper eventMapper,
                         @Value("${platform.runner.process.command}") String[] command,
                         @Value("${platform.runner.process.working-dir}") Path workingDir,
                         @Value("${platform.home}") Path platformHome,
                         @Value("${platform.runner.stop-grace-seconds:15}") long stopGraceSeconds,
                         @Value("${platform.runner.follow-interval-ms:500}") long followIntervalMs) {
        this.specMapper = specMapper;
        this.eventMapper = eventMapper;
        this.command = absoluteExecutable(List.of(command));
        this.workingDir = workingDir.toAbsolutePath();
        this.runsDir = platformHome.resolve("runs").toAbsolutePath();
        this.pricesFile = platformHome.resolve("prices.json").toAbsolutePath();
        this.stopGrace = Duration.ofSeconds(stopGraceSeconds);
        this.followInterval = Duration.ofMillis(followIntervalMs);
    }

    @Override
    public RunHandle start(AnalysisId id, AnalysisSpec spec, Map<String, String> environment, RunEventSink sink) {
        Path runDir = runsDir.resolve(id.value());
        Path specFile = runDir.resolve("spec.json");
        Process process;
        try {
            Files.createDirectories(runDir);
            specWriter.writeValue(specFile.toFile(), specMapper.map(id, spec));
            List<String> args = new ArrayList<>(command);
            args.addAll(List.of("run", "--spec", specFile.toString(), "--out", runDir.toString()));
            ProcessBuilder builder = new ProcessBuilder(args)
                    .directory(workingDir.toFile())
                    .redirectInput(ProcessBuilder.Redirect.from(Path.of("/dev/null").toFile()))
                    .redirectError(ProcessBuilder.Redirect.appendTo(runDir.resolve("run.log").toFile()));
            builder.environment().putAll(environment);
            // The user's own prices, over the ones ta-runner ships; ignored while the file is absent.
            builder.environment().put("TA_RUNNER_PRICES", pricesFile.toString());
            process = builder.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start ta-runner for " + id + ": " + e.getMessage(), e);
        }
        String ref = ref(process.toHandle());
        processes.put(ref, process.toHandle());
        Thread.ofVirtual().name("runner-" + id.value()).start(() -> pump(id, ref, process, sink));
        return new RunHandle(ref);
    }

    @Override
    public void stop(RunHandle handle) {
        ProcessHandle process = processes.get(handle.ref());
        if (process == null) {
            return;
        }
        // Signal through the ProcessHandle: Process.destroy() also closes the child's stdout, and
        // the runner would die of SIGPIPE instead of reporting run_finished{stopped}.
        process.destroy();
        Thread.ofVirtual().name("runner-stop-" + handle.ref()).start(() -> {
            try {
                process.onExit().get(stopGrace.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.warn("Runner {} ignored SIGTERM for {}; killing it", handle.ref(), stopGrace);
                process.destroyForcibly();
            } catch (ExecutionException e) {
                log.warn("Waiting for runner {} to stop failed: {}", handle.ref(), e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Override
    public boolean reattach(AnalysisId id, RunHandle handle, long afterSeq, RunEventSink sink) {
        Optional<ProcessHandle> process = find(handle.ref());
        if (process.isEmpty()) {
            return false;
        }
        processes.put(handle.ref(), process.get());
        Path events = runsDir.resolve(id.value()).resolve("events.jsonl");
        Thread.ofVirtual().name("runner-follow-" + id.value())
                .start(() -> follow(id, handle.ref(), process.get(), events, afterSeq, sink));
        return true;
    }

    /** The live process behind a handle, unless its pid has since gone to another process. */
    private static Optional<ProcessHandle> find(String ref) {
        String[] parts = ref.split("@", 2);
        long pid;
        try {
            pid = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return ProcessHandle.of(pid)
                .filter(ProcessHandle::isAlive)
                .filter(process -> parts.length == 1 || ref(process).equals(ref));
    }

    private static String ref(ProcessHandle process) {
        return process.info().startInstant()
                .map(start -> process.pid() + "@" + start.toEpochMilli())
                .orElse(String.valueOf(process.pid()));
    }

    private void follow(AnalysisId id, String ref, ProcessHandle process, Path events, long afterSeq,
                        RunEventSink sink) {
        EventsFileTail tail = new EventsFileTail(events);
        long lastSeq = afterSeq;
        try {
            boolean alive;
            do {
                alive = process.isAlive();
                // Read once more after the exit: the last lines may have come just before it.
                for (String line : tail.readNewLines()) {
                    try {
                        Optional<RunEvent> event = parser.parse(line).map(eventMapper::map);
                        if (event.isPresent() && event.get().seq() > lastSeq) {
                            lastSeq = event.get().seq();
                            sink.onEvent(event.get());
                        }
                    } catch (RuntimeException e) {
                        log.error("{}: failed to handle a runner event", id, e);
                    }
                }
                if (alive) {
                    Thread.sleep(followInterval);
                }
            } while (alive);
        } catch (IOException e) {
            log.warn("{}: cannot follow {}: {}", id, events, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        processes.remove(ref);
        // Not this platform's child, so its exit code cannot be read.
        sink.onExit(-1);
    }


    private void pump(AnalysisId id, String ref, Process process, RunEventSink sink) {
        try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    parser.parse(line).map(eventMapper::map).ifPresentOrElse(sink::onEvent,
                            () -> log.warn("{}: ignoring a runner output line that is not a protocol event", id));
                } catch (RuntimeException e) {
                    log.error("{}: failed to handle a runner event", id, e);
                }
            }
        } catch (IOException e) {
            log.warn("{}: runner output ended unexpectedly: {}", id, e.getMessage());
        }
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            exitCode = -1;
        }
        processes.remove(ref);
        sink.onExit(exitCode);
    }

    /** A relative executable path ("artifact/ta-runner/.venv/bin/python") is resolved here, not by the OS. */
    private static List<String> absoluteExecutable(List<String> command) {
        if (command.isEmpty()) {
            throw new IllegalArgumentException("platform.runner.process.command must not be empty");
        }
        List<String> resolved = new ArrayList<>(command);
        if (resolved.getFirst().contains("/")) {
            resolved.set(0, Path.of(resolved.getFirst()).toAbsolutePath().normalize().toString());
        }
        return resolved;
    }
}
