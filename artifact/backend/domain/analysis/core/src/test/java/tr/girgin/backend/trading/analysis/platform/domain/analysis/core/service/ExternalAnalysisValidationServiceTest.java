package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRun;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunStats;

class ExternalAnalysisValidationServiceTest {

    private static final Instant END = Instant.parse("2026-01-02T03:04:05Z");

    private final ExternalAnalysisValidationService service =
            new ExternalAnalysisValidationService(new AnalysisTransitionService(new AnalysisSpecService()));

    @Test
    void validRecordsPass() {
        assertThatCode(() -> service.validate(List.of(
                        reportFiles(null),
                        reportFiles(run(AnalysisStatus.COMPLETED, 2)),
                        runHistory(run(AnalysisStatus.FAILED, 1)))))
                .doesNotThrowAnyException();
    }

    @Test
    void aRunHistoryRecordWithoutItsRunIsRejected() {
        assertThatThrownBy(() -> service.validate(List.of(runHistory(null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs its run");
    }

    @Test
    void aRunThatHasNotEndedIsRejected() {
        assertThatThrownBy(() -> service.validate(List.of(runHistory(run(AnalysisStatus.RUNNING, 1)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a finished run");
    }

    @Test
    void debateRoundsBelowOneAreRejected() {
        assertThatThrownBy(() -> service.validate(List.of(reportFiles(run(AnalysisStatus.COMPLETED, 0)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("debateRounds");
    }

    @Test
    void oneInvalidRecordRejectsTheBatch() {
        assertThatThrownBy(() -> service.validate(List.of(reportFiles(null), runHistory(null))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // The records are built inside the call, so the test holds while the models still check themselves.
    private static ExternalAnalysis reportFiles(ExternalRun run) {
        return new ExternalAnalysis(
                ExternalAnalysis.Origin.REPORT_FILES,
                "AAPL",
                LocalDate.of(2026, 1, 1),
                List.of(Analyst.MARKET),
                null,
                null,
                run,
                END);
    }

    private static ExternalAnalysis runHistory(ExternalRun run) {
        return new ExternalAnalysis(
                ExternalAnalysis.Origin.RUN_HISTORY,
                "AAPL",
                LocalDate.of(2026, 1, 1),
                List.of(Analyst.MARKET),
                null,
                null,
                run,
                END);
    }

    private static ExternalRun run(AnalysisStatus status, int debateRounds) {
        return new ExternalRun(
                "run-1",
                status,
                null,
                "openai",
                "deep",
                "quick",
                debateRounds,
                "English",
                new RunStats(0, 0, 0, 0, null, Duration.ZERO),
                null,
                END);
    }
}
