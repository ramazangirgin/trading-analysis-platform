package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.runner;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import com.github.dockerjava.api.model.SELContext;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline.RunnerOutputLineParser;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline.mapper.RunnerOutputLineToRunEventMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.runner.mapper.AnalysisSpecToRunnerSpecMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.runner.support.DockerClients;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.runner.support.RunnerKind;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunEventSink;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunHandle;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunnerPort;

/**
 * Runs each analysis in its own ta-runner container (PLAN.md section 4, option B; D8). The handle
 * is the container id.
 *
 * <ul>
 *   <li>The spec goes in as {@code TA_RUNNER_SPEC}; no platform directory is mounted. The only
 *       mount is the TradingAgents data dir (a volume or a host path), where upstream writes its
 *       reports and caches.</li>
 *   <li>Events come back on the container's stdout. Each one is written to the run's
 *       {@code events.jsonl} before the sink sees it, as ta-runner does itself in the process
 *       runner; stderr goes to {@code run.log}.</li>
 *   <li>The log stream is reopened when it breaks (a long LLM call can be silent for minutes),
 *       from the last timestamp seen; events are told apart by {@code seq}.</li>
 *   <li>A container is removed only after its exit was handed to the sink, so one that ended
 *       while the platform was down still has its logs when {@link #reattach} reads them.</li>
 *   <li>Locked down: read-only root filesystem with a tmpfs {@code /tmp}, no capabilities,
 *       no-new-privileges, memory and CPU limits, the image's non-root user, and only the
 *       configured image is ever started.</li>
 * </ul>
 */
@Component
@Conditional(RunnerKind.Docker.class)
class DockerRunnerAdapter implements RunnerPort, DisposableBean {

    static final String LABEL_MANAGED = "ta.platform.managed";
    static final String LABEL_RUN_ID = "ta.platform.run_id";
    static final String SPEC_ENV = "TA_RUNNER_SPEC";
    static final String PRICES_ENV = "TA_RUNNER_PRICES_JSON";

    private static final Logger log = LoggerFactory.getLogger(DockerRunnerAdapter.class);
    private static final String DATA_DIR = "/home/runner/.tradingagents";
    // Exited containers nobody follows are removed once they are this old (a crash between an
    // exit and its removal leaves one behind).
    private static final Duration LEFTOVER_AGE = Duration.ofMinutes(10);
    private static final Duration RECONNECT_PAUSE = Duration.ofSeconds(2);
    private static final long BYTES_PER_MB = 1024L * 1024;
    private static final long NANO_CPUS_PER_CPU = 1_000_000_000L;
    // Docker's short container id, as `docker ps` shows it.
    private static final int SHORT_ID_LENGTH = 12;

    private final AnalysisSpecToRunnerSpecMapper specMapper;
    private final RunnerOutputLineToRunEventMapper eventMapper;
    private final RunnerOutputLineParser parser = new RunnerOutputLineParser();
    private final JsonMapper specWriter = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();
    private final DockerClient docker;
    private final String image;
    private final String dataMount;
    private final String network;
    private final long memoryBytes;
    private final long nanoCpus;
    private final String tmpSize;
    private final Path runsDir;
    private final Path pricesFile;
    private final int stopGraceSeconds;
    // Containers a thread is following; stop() and the leftover clean-up look here.
    private final Set<String> followed = ConcurrentHashMap.newKeySet();

    @Autowired
    @SuppressWarnings("checkstyle:ParameterNumber") // one @Value per runner setting
    DockerRunnerAdapter(
            AnalysisSpecToRunnerSpecMapper specMapper,
            RunnerOutputLineToRunEventMapper eventMapper,
            @Value("${platform.runner.docker.host}") String host,
            @Value("${platform.runner.docker.image}") String image,
            @Value("${platform.runner.docker.data-mount}") String dataMount,
            @Value("${platform.runner.docker.network:}") String network,
            @Value("${platform.runner.docker.memory-mb:2048}") long memoryMb,
            @Value("${platform.runner.docker.cpus:2}") double cpus,
            @Value("${platform.runner.docker.tmp-size-mb:512}") long tmpSizeMb,
            @Value("${platform.runner.docker.log-silence-minutes:10}") long logSilenceMinutes,
            @Value("${platform.home}") Path platformHome,
            @Value("${platform.runner.stop-grace-seconds:15}") int stopGraceSeconds) {
        this(
                specMapper,
                eventMapper,
                DockerClients.create(host, Duration.ofMinutes(logSilenceMinutes)),
                image,
                dataMount,
                network,
                memoryMb,
                cpus,
                tmpSizeMb,
                platformHome,
                stopGraceSeconds);
    }

    @SuppressWarnings("checkstyle:ParameterNumber") // the runner settings above, with the Docker client given
    DockerRunnerAdapter(
            AnalysisSpecToRunnerSpecMapper specMapper,
            RunnerOutputLineToRunEventMapper eventMapper,
            DockerClient docker,
            String image,
            String dataMount,
            String network,
            long memoryMb,
            double cpus,
            long tmpSizeMb,
            Path platformHome,
            int stopGraceSeconds) {
        this.specMapper = specMapper;
        this.eventMapper = eventMapper;
        this.docker = docker;
        this.image = image;
        this.dataMount = dataMount;
        this.network = network == null ? "" : network.strip();
        this.memoryBytes = memoryMb * BYTES_PER_MB;
        this.nanoCpus = Math.round(cpus * NANO_CPUS_PER_CPU);
        this.tmpSize = tmpSizeMb + "m";
        this.runsDir = platformHome.resolve("runs").toAbsolutePath();
        this.pricesFile = platformHome.resolve("prices.json").toAbsolutePath();
        this.stopGraceSeconds = stopGraceSeconds;
    }

    @Override
    public RunHandle start(AnalysisId id, AnalysisSpec spec, Map<String, String> environment, RunEventSink sink) {
        cleanUpLeftovers();
        Path runDir = runsDir.resolve(id.value());
        String specJson;
        try {
            Files.createDirectories(runDir);
            specJson = specWriter.writeValueAsString(specMapper.map(id, spec));
            // Kept for the record, like the process runner's; the container gets it from the environment.
            Files.writeString(runDir.resolve("spec.json"), specJson);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not prepare " + runDir + ": " + e.getMessage(), e);
        }
        List<String> env = new ArrayList<>();
        environment.forEach((name, value) -> env.add(name + "=" + value));
        env.add(SPEC_ENV + "=" + specJson);
        userPrices().ifPresent(prices -> env.add(PRICES_ENV + "=" + prices));

        String containerId;
        try {
            containerId = docker.createContainerCmd(image)
                    .withName("ta-run-" + id.value())
                    .withCmd("run", "--spec-env", SPEC_ENV, "--out", "/tmp/run")
                    .withEnv(env)
                    .withLabels(Map.of(LABEL_MANAGED, "true", LABEL_RUN_ID, id.value()))
                    .withHostConfig(hostConfig())
                    .exec()
                    .getId();
            docker.startContainerCmd(containerId).exec();
        } catch (DockerException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Could not start a ta-runner container (" + image + ") for " + id + ": " + e.getMessage(), e);
        }
        follow(id, containerId, 0, false, sink);
        return new RunHandle(containerId);
    }

    @Override
    public void stop(RunHandle handle) {
        Thread.ofVirtual().name("runner-stop-" + shortId(handle.ref())).start(() -> {
            try {
                // SIGTERM (ta-runner reports run_finished{stopped}), SIGKILL after the grace period.
                docker.stopContainerCmd(handle.ref())
                        .withTimeout(stopGraceSeconds)
                        .exec();
            } catch (NotModifiedException _) {
                log.debug("Container {} was not running", shortId(handle.ref()));
            } catch (DockerException e) {
                if (!DockerClients.isNoSuchContainer(e)) {
                    log.warn("Could not stop container {}: {}", shortId(handle.ref()), e.getMessage());
                }
            }
        });
    }

    @Override
    public boolean reattach(AnalysisId id, RunHandle handle, long afterSeq, RunEventSink sink) {
        InspectContainerResponse container;
        try {
            container = docker.inspectContainerCmd(handle.ref()).exec();
        } catch (DockerException e) {
            if (DockerClients.isNoSuchContainer(e)) {
                return false;
            }
            log.warn("Cannot inspect container {} of {}: {}", shortId(handle.ref()), id, e.getMessage());
            return false;
        }
        Map<String, String> labels =
                container.getConfig() == null ? null : container.getConfig().getLabels();
        if (labels == null || !id.value().equals(labels.get(LABEL_RUN_ID))) {
            return false;
        }
        // It may have ended meanwhile: its logs are still there, so its outcome is read all the same.
        follow(id, handle.ref(), afterSeq, true, sink);
        return true;
    }

    @Override
    public void destroy() throws IOException {
        docker.close();
    }

    private HostConfig hostConfig() {
        HostConfig config = HostConfig.newHostConfig()
                .withReadonlyRootfs(true)
                .withTmpFs(Map.of("/tmp", "rw,nosuid,nodev,size=" + tmpSize))
                .withCapDrop(Capability.ALL)
                .withSecurityOpts(List.of("no-new-privileges"))
                .withMemory(memoryBytes)
                .withNanoCPUs(nanoCpus);
        if (dataMount.startsWith("/")) {
            // A folder on the host, shared with the platform: labelled for container use on SELinux
            // hosts, as Compose's :z does (ignored elsewhere).
            config.withBinds(new Bind(dataMount, new Volume(DATA_DIR), AccessMode.rw, SELContext.shared));
        } else {
            config.withMounts(List.of(
                    new Mount().withType(MountType.VOLUME).withSource(dataMount).withTarget(DATA_DIR)));
        }
        return network.isEmpty() ? config : config.withNetworkMode(network);
    }

    private Optional<String> userPrices() {
        try {
            return Files.isRegularFile(pricesFile) ? Optional.of(Files.readString(pricesFile)) : Optional.empty();
        } catch (IOException e) {
            log.warn("Ignoring unreadable {}: {}", pricesFile, e.getMessage());
            return Optional.empty();
        }
    }

    /** Streams a container's output into the run's files and the sink until it exits, then removes it. */
    private void follow(AnalysisId id, String containerId, long afterSeq, boolean reattached, RunEventSink sink) {
        followed.add(containerId);
        Path runDir = runsDir.resolve(id.value());
        try {
            Files.createDirectories(runDir);
        } catch (IOException e) {
            log.warn("{}: cannot create {}: {}", id, runDir, e.getMessage());
        }
        Thread.ofVirtual().name("runner-" + id.value()).start(() -> {
            OutputPump pump = new OutputPump(id, runDir, afterSeq, sink, reattached ? runLogModified(runDir) : null);
            int exitCode = pumpUntilExit(id, containerId, pump);
            followed.remove(containerId);
            sink.onExit(exitCode);
            remove(containerId);
        });
    }

    @SuppressWarnings("checkstyle:IllegalCatch") // keep following the container through any failure
    private int pumpUntilExit(AnalysisId id, String containerId, OutputPump pump) {
        while (true) {
            try {
                LogCallback callback = new LogCallback(pump);
                var command = docker.logContainerCmd(containerId)
                        .withStdOut(true)
                        .withStdErr(true)
                        .withTimestamps(true)
                        .withFollowStream(true);
                pump.resumeFrom().ifPresent(since -> command.withSince((int) since.getEpochSecond()));
                command.exec(callback).awaitCompletion();
                pump.flush();
                InspectContainerResponse.ContainerState state =
                        docker.inspectContainerCmd(containerId).exec().getState();
                if (state == null || !Boolean.TRUE.equals(state.getRunning())) {
                    Long exit = state == null ? null : state.getExitCodeLong();
                    return exit == null ? -1 : exit.intValue();
                }
                log.debug("{}: log stream of {} ended while it runs; reopening", id, shortId(containerId));
            } catch (DockerException e) {
                if (DockerClients.isNoSuchContainer(e)) {
                    log.warn("{}: container {} is gone", id, shortId(containerId));
                    return -1;
                }
                log.warn("{}: following container {} failed ({}); retrying", id, shortId(containerId), e.getMessage());
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return -1;
            } catch (RuntimeException e) {
                log.warn("{}: following container {} failed ({}); retrying", id, shortId(containerId), e.getMessage());
            }
            try {
                Thread.sleep(RECONNECT_PAUSE);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return -1;
            }
        }
    }

    private void remove(String containerId) {
        try {
            docker.removeContainerCmd(containerId).withForce(true).exec();
        } catch (DockerException e) {
            if (!DockerClients.isNoSuchContainer(e)) {
                log.warn("Could not remove container {}: {}", shortId(containerId), e.getMessage());
            }
        }
    }

    /** Exited runner containers no thread follows any more, left behind by a crash. */
    private void cleanUpLeftovers() {
        try {
            List<Container> exited = docker.listContainersCmd()
                    .withShowAll(true)
                    .withLabelFilter(Map.of(LABEL_MANAGED, "true"))
                    .withStatusFilter(List.of("exited", "dead"))
                    .exec();
            Instant cutoff = Instant.now().minus(LEFTOVER_AGE);
            for (Container container : exited) {
                if (!followed.contains(container.getId()) && finishedBefore(container.getId(), cutoff)) {
                    log.info(
                            "Removing leftover runner container {} ({})",
                            shortId(container.getId()),
                            container.getLabels().get(LABEL_RUN_ID));
                    remove(container.getId());
                }
            }
        } catch (DockerException e) {
            log.warn("Could not list runner containers: {}", e.getMessage());
        }
    }

    private boolean finishedBefore(String containerId, Instant cutoff) {
        try {
            String finished =
                    docker.inspectContainerCmd(containerId).exec().getState().getFinishedAt();
            return finished != null && Instant.parse(finished).isBefore(cutoff);
        } catch (DockerException | DateTimeParseException | NullPointerException _) {
            return false;
        }
    }

    private static Instant runLogModified(Path runDir) {
        try {
            Path runLog = runDir.resolve("run.log");
            return Files.exists(runLog) ? Files.getLastModifiedTime(runLog).toInstant() : null;
        } catch (IOException _) {
            return null;
        }
    }

    private static String shortId(String containerId) {
        return containerId.length() > SHORT_ID_LENGTH ? containerId.substring(0, SHORT_ID_LENGTH) : containerId;
    }

    private static final class LogCallback extends ResultCallback.Adapter<Frame> {

        private final OutputPump pump;

        LogCallback(OutputPump pump) {
            this.pump = pump;
        }

        @Override
        public void onNext(Frame frame) {
            pump.accept(frame.getStreamType(), frame.getPayload());
        }
    }

    /**
     * Turns log frames into lines ({@code <RFC 3339 timestamp> <text>}): stdout lines are protocol
     * events, stderr lines the run log. Frames need not end at a line break, so each stream keeps
     * its unfinished line.
     */
    private final class OutputPump {

        private final AnalysisId id;
        private final Path eventsFile;
        private final Path runLog;
        private final RunEventSink sink;
        private final Map<StreamType, StringBuilder> partial = new EnumMap<>(StreamType.class);
        // Stderr written before this (already in run.log from before a restart) is not repeated.
        private final Instant stderrAfter;
        private final Map<StreamType, Instant> lastTimestamps = new EnumMap<>(StreamType.class);
        private long lastSeq;

        OutputPump(AnalysisId id, Path runDir, long afterSeq, RunEventSink sink, Instant stderrAfter) {
            this.id = id;
            this.eventsFile = runDir.resolve("events.jsonl");
            this.runLog = runDir.resolve("run.log");
            this.sink = sink;
            this.lastSeq = afterSeq;
            this.stderrAfter = stderrAfter;
        }

        /** Where a reopened stream starts: the earliest of each stream's last line, so neither misses one. */
        Optional<Instant> resumeFrom() {
            return lastTimestamps.values().stream().min(Instant::compareTo);
        }

        void accept(StreamType stream, byte[] payload) {
            StringBuilder buffer = partial.computeIfAbsent(stream, _ -> new StringBuilder());
            buffer.append(new String(payload, StandardCharsets.UTF_8));
            int newline;
            while ((newline = buffer.indexOf("\n")) >= 0) {
                String line = buffer.substring(0, newline);
                buffer.delete(0, newline + 1);
                line(stream, line);
            }
        }

        /** A stream that ended without a final line break still has its last line. */
        void flush() {
            partial.forEach((stream, buffer) -> {
                if (!buffer.isEmpty()) {
                    line(stream, buffer.toString());
                    buffer.setLength(0);
                }
            });
        }

        private void line(StreamType stream, String raw) {
            int space = raw.indexOf(' ');
            Instant timestamp = space > 0 ? timestamp(raw.substring(0, space)) : null;
            // No timestamp prefix: keep the line whole.
            String text = timestamp == null ? raw : raw.substring(space + 1);
            if (timestamp != null && repeated(stream, timestamp)) {
                return;
            }
            if (stream == StreamType.STDERR) {
                if (stderrAfter == null || timestamp == null || timestamp.isAfter(stderrAfter)) {
                    append(runLog, text);
                }
                return;
            }
            event(text);
        }

        private static Instant timestamp(String prefix) {
            try {
                return Instant.parse(prefix);
            } catch (DateTimeParseException _) {
                return null;
            }
        }

        /** A reopened stream repeats lines from the second it resumes at; they are skipped. */
        private boolean repeated(StreamType stream, Instant timestamp) {
            Instant last = lastTimestamps.get(stream);
            if (last != null && !timestamp.isAfter(last)) {
                return true;
            }
            lastTimestamps.put(stream, timestamp);
            return false;
        }

        @SuppressWarnings("checkstyle:IllegalCatch") // one bad event must not stop the log stream
        private void event(String text) {
            try {
                Optional<RunEvent> event = parser.parse(text).map(eventMapper::map);
                if (event.isEmpty()) {
                    log.warn("{}: ignoring a runner output line that is not a protocol event", id);
                } else if (event.get().seq() > lastSeq) {
                    lastSeq = event.get().seq();
                    // File first: a client replaying events.jsonl must find what the sink published.
                    append(eventsFile, text);
                    sink.onEvent(event.get());
                }
            } catch (RuntimeException e) {
                log.error("{}: failed to handle a runner event", id, e);
            }
        }

        private void append(Path file, String line) {
            try {
                Files.writeString(
                        file,
                        line + "\n",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                log.warn("{}: cannot write {}: {}", id, file.getFileName(), e.getMessage());
            }
        }
    }
}
