package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.AdapterTestSupport;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline.mapper.RunnerOutputLineToRunEventMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.runner.mapper.AnalysisSpecToRunnerSpecMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.runner.support.DockerClients;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunOutcome;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunEventSink;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunHandle;

/**
 * Against a real Docker Engine API (Docker, or Podman's compatible socket), with a fake runner
 * image built from Alpine: a shell script that speaks the event protocol. Skipped when no engine
 * answers at {@code DOCKER_HOST}.
 */
class DockerRunnerAdapterTest extends AdapterTestSupport {

    private static final String IMAGE = "ta-runner-fake:test";
    private static final String HOST = System.getenv().getOrDefault("DOCKER_HOST", "unix:///var/run/docker.sock");
    private static final String VOLUME = "ta-runner-test-" + UUID.randomUUID().toString().substring(0, 8);
    @SuppressWarnings("checkstyle:LineLength") // runner protocol lines in a fixture stay on one line
    private static final String SCRIPT = """
            #!/bin/sh
            # Called as: run --spec-env TA_RUNNER_SPEC --out /tmp/run
            ev() { printf '{"v":1,"ts":"2026-09-30T10:00:0%sZ","run_id":"x","seq":%s,"type":"%s"%s}\\n' "$1" "$1" "$2" "$3"; }
            trap 'ev 9 run_finished ",\\"status\\":\\"stopped\\""; exit 2' TERM
            echo "stderr: starting $1 $2 $3" >&2
            ev 1 run_started ',"spec":{}'
            if touch /etc/probe 2>/dev/null; then root=writable; else root=read-only; fi
            if touch /home/runner/.tradingagents/probe 2>/dev/null; then data=writable; else data=read-only; fi
            ev 2 log ",\\"message\\":\\"root=$root data=$data key=$DEEPSEEK_API_KEY\\""
            case "$TA_RUNNER_SPEC" in
              *SLOW*) i=0; while [ $i -lt 40 ]; do sleep 0.25; i=$((i+1)); done ;;
            esac
            ev 3 decision ',"rating":"Hold","raw":"Hold"'
            echo "stderr: done" >&2
            ev 4 run_finished ',"status":"completed"'
            """;

    private static DockerClient docker;

    @Autowired
    private AnalysisSpecToRunnerSpecMapper specMapper;
    @Autowired
    private RunnerOutputLineToRunEventMapper eventMapper;

    @TempDir
    private Path home;

    @BeforeAll
    @SuppressWarnings("checkstyle:IllegalCatch") // any failure means no usable engine: skip
    static void buildFakeRunnerImage(@TempDir Path context) throws IOException {
        docker = DockerClients.create(HOST, Duration.ofMinutes(2));
        try {
            docker.pingCmd().exec();
        } catch (RuntimeException e) {
            assumeTrue(false, "No Docker engine at " + HOST + ": " + e.getMessage());
        }
        Files.writeString(context.resolve("ta-runner"), SCRIPT);
        Files.writeString(context.resolve("Dockerfile"), """
                FROM docker.io/library/alpine:3
                RUN adduser -D -u 10001 runner && mkdir -p /home/runner/.tradingagents \\
                    && chown runner /home/runner/.tradingagents
                COPY ta-runner /usr/local/bin/ta-runner
                USER runner
                ENTRYPOINT ["/bin/sh", "/usr/local/bin/ta-runner"]
                """);
        docker.buildImageCmd(context.toFile()).withTags(Set.of(IMAGE)).exec(new BuildImageResultCallback())
                .awaitImageId(5, TimeUnit.MINUTES);
    }

    @AfterAll
    @SuppressWarnings("checkstyle:IllegalCatch") // best-effort cleanup
    static void removeVolume() {
        if (docker != null) {
            try {
                docker.removeVolumeCmd(VOLUME).exec();
            } catch (RuntimeException _) {
                // Not created, or still in use by a container being removed.
            }
        }
    }

    @Test
    void runsTheAnalysisInALockedDownContainerAndRemovesIt() throws Exception {
        AnalysisId id = AnalysisId.newId();
        RecordingSink sink = new RecordingSink();

        RunHandle handle = adapter(home).start(id, spec("NVDA"), Map.of("DEEPSEEK_API_KEY", "sk-docker"), sink);

        assertThat(sink.awaitExit()).isZero();
        assertThat(sink.events).extracting(RunEvent::seq).containsExactly(1L, 2L, 3L, 4L);
        assertThat(sink.events.get(1).payload().get("message"))
                .isEqualTo("root=read-only data=writable key=sk-docker");
        assertThat(sink.events.getLast().outcome()).isEqualTo(RunOutcome.COMPLETED);
        Path runDir = home.resolve("runs").resolve(id.value());
        assertThat(Files.readAllLines(runDir.resolve("events.jsonl"))).hasSize(4);
        assertThat(Files.readString(runDir.resolve("run.log")))
                .contains("stderr: starting run --spec-env TA_RUNNER_SPEC", "stderr: done");
        assertThat(Files.readString(runDir.resolve("spec.json"))).contains("\"run_id\":\"" + id.value() + "\"");
        awaitRemoved(handle.ref());
    }

    @Test
    void stopEndsTheRunCleanly() throws Exception {
        RecordingSink sink = new RecordingSink();
        DockerRunnerAdapter adapter = adapter(home);
        RunHandle handle = adapter.start(AnalysisId.newId(), spec("SLOW"), Map.of(), sink);
        sink.awaitEvents(2);

        adapter.stop(handle);

        assertThat(sink.awaitExit()).isEqualTo(2);
        assertThat(sink.events.getLast().outcome()).isEqualTo(RunOutcome.STOPPED);
    }

    @Test
    void aRestartedPlatformFollowsARunningContainerAgain(@TempDir Path restartedHome) throws Exception {
        AnalysisId id = AnalysisId.newId();
        RecordingSink before = new RecordingSink();
        RunHandle handle = adapter(home).start(id, spec("SLOW"), Map.of(), before);
        before.awaitEvents(2);

        // A second adapter plays the platform after a restart; it had seen events up to seq 2.
        RecordingSink after = new RecordingSink();
        boolean followed = adapter(restartedHome).reattach(id, handle, 2, after);

        assertThat(followed).isTrue();
        assertThat(after.awaitExit()).isZero();
        assertThat(after.events).extracting(RunEvent::seq).containsExactly(3L, 4L);
        assertThat(Files.readAllLines(restartedHome.resolve("runs").resolve(id.value()).resolve("events.jsonl")))
                .hasSize(2);
        before.awaitExit();
    }

    @Test
    void aRunThatEndedWhileThePlatformWasDownKeepsItsOutcome() throws Exception {
        AnalysisId id = AnalysisId.newId();
        // Started by "the old platform", which never read its end.
        String containerId = docker.createContainerCmd(IMAGE)
                .withCmd("run", "--spec-env", DockerRunnerAdapter.SPEC_ENV, "--out", "/tmp/run")
                .withLabels(Map.of(
                        DockerRunnerAdapter.LABEL_MANAGED, "true", DockerRunnerAdapter.LABEL_RUN_ID, id.value()))
                .withHostConfig(HostConfig.newHostConfig().withMounts(List.of(new Mount()
                        .withType(MountType.VOLUME).withSource(VOLUME).withTarget("/home/runner/.tradingagents"))))
                .exec().getId();
        docker.startContainerCmd(containerId).exec();
        docker.waitContainerCmd(containerId).start().awaitStatusCode(60, TimeUnit.SECONDS);
        RecordingSink sink = new RecordingSink();

        boolean followed = adapter(home).reattach(id, new RunHandle(containerId), 1, sink);

        assertThat(followed).isTrue();
        assertThat(sink.awaitExit()).isZero();
        assertThat(sink.events).extracting(RunEvent::seq).containsExactly(2L, 3L, 4L);
        awaitRemoved(containerId);
    }

    @Test
    void doesNotFollowContainersThatAreGoneOrBelongToAnotherRun() {
        DockerRunnerAdapter adapter = adapter(home);

        assertThat(adapter.reattach(AnalysisId.newId(), new RunHandle("0123456789ab"), 0, new RecordingSink()))
                .isFalse();
    }

    private DockerRunnerAdapter adapter(Path platformHome) {
        return new DockerRunnerAdapter(specMapper, eventMapper, docker, IMAGE, VOLUME, "", 256, 1, 64,
                platformHome, 3);
    }

    private static void awaitRemoved(String containerId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            try {
                docker.inspectContainerCmd(containerId).exec();
            } catch (DockerException e) {
                if (DockerClients.isNoSuchContainer(e)) {
                    return;
                }
                throw e;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("container " + containerId + " was not removed");
    }

    @SuppressWarnings("checkstyle:VisibilityModifier") // test double: tests read its state directly
    private static final class RecordingSink implements RunEventSink {

        final List<RunEvent> events = new CopyOnWriteArrayList<>();
        final CountDownLatch exited = new CountDownLatch(1);
        final AtomicInteger exitCode = new AtomicInteger(Integer.MIN_VALUE);

        @Override
        public void onEvent(RunEvent event) {
            events.add(event);
        }

        @Override
        public void onExit(int code) {
            exitCode.set(code);
            exited.countDown();
        }

        int awaitExit() throws InterruptedException {
            assertThat(exited.await(90, TimeUnit.SECONDS)).as("runner exited").isTrue();
            return exitCode.get();
        }

        void awaitEvents(int count) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (events.size() < count && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertThat(events).hasSizeGreaterThanOrEqualTo(count);
        }
    }
}
