package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.AdapterTestSupport;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEventType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunOutcome;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunEventSink;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunHandle;

class ProcessRunnerAdapterTest extends AdapterTestSupport {

    @Autowired
    private ProcessRunnerAdapter runner;

    @Test
    void writesTheSpecStreamsEventsAndReportsTheExit() throws Exception {
        script("""
                echo "upstream noise on stderr" >&2
                echo '{"v":1,"ts":"2026-09-29T10:00:00.000Z","run_id":"x","seq":1,"type":"run_started","spec":{}}'
                echo 'not a protocol line'
                printf '{"v":1,"ts":"2026-09-29T10:00:01Z","run_id":"x","seq":2,"type":"log","message":"%s"}\\n' "$DEEPSEEK_API_KEY"
                echo '{"v":1,"ts":"2026-09-29T10:00:02Z","run_id":"x","seq":3,"type":"decision","rating":"Hold","raw":"Hold"}'
                echo '{"v":1,"ts":"2026-09-29T10:00:03Z","run_id":"x","seq":4,"type":"run_finished","status":"completed"}'
                exit 0
                """);
        AnalysisId id = AnalysisId.newId();
        RecordingSink sink = new RecordingSink();

        runner.start(id, spec("NVDA"), Map.of("DEEPSEEK_API_KEY", "sk-from-env"), sink);

        assertThat(sink.awaitExit()).isEqualTo(0);
        assertThat(sink.events).extracting(RunEvent::seq).containsExactly(1L, 2L, 3L, 4L);
        assertThat(sink.events.get(1).payload()).containsEntry("message", "sk-from-env");
        assertThat(sink.events.get(2).rating()).isEqualTo(Rating.HOLD);
        assertThat(sink.events.get(3).outcome()).isEqualTo(RunOutcome.COMPLETED);

        Path runDir = HOME.resolve("runs").resolve(id.value());
        assertThat(Files.readString(runDir.resolve("run.log"))).contains("upstream noise on stderr");
        JsonNode spec = JsonMapper.builder().build().readTree(runDir.resolve("spec.json").toFile());
        assertThat(spec.get("run_id").asString()).isEqualTo(id.value());
        assertThat(spec.get("ticker").asString()).isEqualTo("NVDA");
        assertThat(spec.get("trade_date").asString()).isEqualTo("2026-09-25");
        assertThat(spec.get("asset_type").asString()).isEqualTo("stock");
        assertThat(spec.get("analysts").toString()).isEqualTo("[\"market\",\"news\"]");
        assertThat(spec.get("llm_provider").asString()).isEqualTo("deepseek");
        assertThat(spec.get("max_risk_discuss_rounds").asInt()).isEqualTo(2);
        assertThat(spec.get("output_language").asString()).isEqualTo("Turkish");
        assertThat(spec.get("checkpoint_enabled").asBoolean()).isTrue();
    }

    @Test
    void passesTheRunArguments() throws Exception {
        script("""
                printf '{"v":1,"ts":"2026-09-29T10:00:00Z","run_id":"x","seq":1,"type":"log","message":"%s"}\\n' "$*"
                """);
        AnalysisId id = AnalysisId.newId();
        RecordingSink sink = new RecordingSink();

        runner.start(id, spec("MU"), Map.of(), sink);

        sink.awaitExit();
        Path runDir = HOME.resolve("runs").resolve(id.value()).toAbsolutePath();
        assertThat(sink.events.getFirst().payload().get("message"))
                .isEqualTo("run --spec " + runDir.resolve("spec.json") + " --out " + runDir);
    }

    @Test
    void stopSendsSigtermAndTheRunnerEndsCleanly() throws Exception {
        script("""
                STOPPED='{"v":1,"ts":"2026-09-29T10:00:09Z","run_id":"x","seq":2,"type":"run_finished","status":"stopped"}'
                trap 'echo "$STOPPED"; exit 2' TERM
                echo '{"v":1,"ts":"2026-09-29T10:00:00Z","run_id":"x","seq":1,"type":"run_started"}'
                while true; do sleep 0.1; done
                """);
        RecordingSink sink = new RecordingSink();
        RunHandle handle = runner.start(AnalysisId.newId(), spec("GOOG"), Map.of(), sink);
        sink.awaitEvents(1);

        runner.stop(handle);

        assertThat(sink.awaitExit()).isEqualTo(2);
        assertThat(sink.events.getLast().type()).isEqualTo(RunEventType.RUN_FINISHED);
        assertThat(sink.events.getLast().outcome()).isEqualTo(RunOutcome.STOPPED);
    }

    @Test
    void killsARunnerThatIgnoresSigterm() throws Exception {
        script("""
                trap '' TERM
                echo '{"v":1,"ts":"2026-09-29T10:00:00Z","run_id":"x","seq":1,"type":"run_started"}'
                while true; do sleep 0.1; done
                """);
        RecordingSink sink = new RecordingSink();
        RunHandle handle = runner.start(AnalysisId.newId(), spec("AAPL"), Map.of(), sink);
        sink.awaitEvents(1);

        runner.stop(handle);

        assertThat(sink.awaitExit()).isNotEqualTo(0);
    }

    private static void script(String body) throws IOException {
        Files.writeString(RUNNER_SCRIPT, "#!/bin/sh\n" + body);
    }

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
            assertThat(exited.await(15, TimeUnit.SECONDS)).as("runner exited").isTrue();
            return exitCode.get();
        }

        void awaitEvents(int count) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (events.size() < count && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertThat(events).hasSizeGreaterThanOrEqualTo(count);
        }
    }
}
