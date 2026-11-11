package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.AdapterTestSupport;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEventType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunOutcome;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.TestAnalyses;

class JsonlEventStoreAdapterTest extends AdapterTestSupport {

    @Autowired
    private JsonlEventStoreAdapter jsonlEventStoreAdapter;

    @Test
    void readsTheRunnersEventsAfterACursor() throws Exception {
        AnalysisId id = TestAnalyses.newId();
        Path file = HOME.resolve("runs").resolve(id.value()).resolve("events.jsonl");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                {"v":1,"ts":"2026-09-29T10:00:00Z","run_id":"x","seq":1,"type":"run_started"}
                {"v":1,"ts":"2026-09-29T10:00:01Z","run_id":"x","seq":2,"type":"message","content":"hi"}
                garbage
                {"v":1,"ts":"2026-09-29T10:00:02Z","run_id":"x","seq":3,"type":"log","message":"x"}
                """);

        assertThat(jsonlEventStoreAdapter.read(id, 1)).extracting(RunEvent::seq).containsExactly(2L, 3L);
    }

    @Test
    void aRunWithoutEventsHasNone() {
        assertThat(jsonlEventStoreAdapter.read(TestAnalyses.newId(), 0)).isEmpty();
    }

    @Test
    void appendedEventsReadBackTheSame() {
        AnalysisId id = TestAnalyses.newId();
        RunEvent died = new RunEvent(
                1,
                Instant.parse("2026-09-29T10:00:00Z"),
                RunEventType.RUN_FINISHED,
                null,
                RunOutcome.FAILED,
                null,
                "Runner exited with code 137",
                Map.of("status", "error", "error_code", "runner_died", "error", "Runner exited with code 137"));

        jsonlEventStoreAdapter.append(id, died);

        assertThat(jsonlEventStoreAdapter.read(id, 0)).singleElement().satisfies(event -> {
            assertThat(event.seq()).isEqualTo(1);
            assertThat(event.type()).isEqualTo(RunEventType.RUN_FINISHED);
            assertThat(event.outcome()).isEqualTo(RunOutcome.FAILED);
            assertThat(event.error()).isEqualTo("Runner exited with code 137");
            assertThat(event.payload()).containsEntry("error_code", "runner_died");
        });
    }
}
