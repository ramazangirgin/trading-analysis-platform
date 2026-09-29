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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline.RunnerOutputLineParser;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline.RunnerOutputLineToRunEventMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunEventSink;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunHandle;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunnerPort;

/**
 * Runs ta-runner as a local child process (PLAN.md section 4, option A): one process per run,
 * started with an argument list (never a shell), stdout read line by line on a virtual thread,
 * stderr kept as {@code run.log} in the run directory.
 */
@Component
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
    private final Duration stopGrace;
    private final Map<String, Process> processes = new ConcurrentHashMap<>();

    ProcessRunnerAdapter(AnalysisSpecToRunnerSpecMapper specMapper,
                         RunnerOutputLineToRunEventMapper eventMapper,
                         @Value("${platform.runner.process.command}") String[] command,
                         @Value("${platform.runner.process.working-dir}") Path workingDir,
                         @Value("${platform.home}") Path platformHome,
                         @Value("${platform.runner.stop-grace-seconds:15}") long stopGraceSeconds) {
        this.specMapper = specMapper;
        this.eventMapper = eventMapper;
        this.command = absoluteExecutable(List.of(command));
        this.workingDir = workingDir.toAbsolutePath();
        this.runsDir = platformHome.resolve("runs").toAbsolutePath();
        this.stopGrace = Duration.ofSeconds(stopGraceSeconds);
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
            process = builder.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start ta-runner for " + id + ": " + e.getMessage(), e);
        }
        String ref = String.valueOf(process.pid());
        processes.put(ref, process);
        Thread.ofVirtual().name("runner-" + id.value()).start(() -> pump(id, ref, process, sink));
        return new RunHandle(ref);
    }

    @Override
    public void stop(RunHandle handle) {
        Process process = processes.get(handle.ref());
        if (process == null) {
            return;
        }
        // Signal through the ProcessHandle: Process.destroy() also closes the child's stdout, and
        // the runner would die of SIGPIPE instead of reporting run_finished{stopped}.
        process.toHandle().destroy();
        Thread.ofVirtual().name("runner-stop-" + handle.ref()).start(() -> {
            try {
                if (!process.waitFor(stopGrace.toMillis(), TimeUnit.MILLISECONDS)) {
                    log.warn("Runner {} ignored SIGTERM for {}; killing it", handle.ref(), stopGrace);
                    process.toHandle().destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
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
