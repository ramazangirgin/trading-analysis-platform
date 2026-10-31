package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSource;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRun;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunOutcome;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunStats;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;

class AnalysisTransitionServiceTest {

    private static final OwnerId OWNER = new OwnerId("owner");
    private static final Instant NOW = Instant.parse("2026-10-01T05:06:07.123456789Z");
    private static final Instant LATER = NOW.plusSeconds(60);

    private final AnalysisTransitionService service = new AnalysisTransitionService(new AnalysisSpecService());

    private Analysis queued() {
        return service.queued(new AnalysisId("r_1"), Fakes.spec("NVDA"), OWNER, NOW);
    }

    @Test
    void queuedIsPrivateAndPlatformOwnedWithEmptyStats() {
        Analysis analysis = queued();

        assertThat(analysis.status()).isEqualTo(AnalysisStatus.QUEUED);
        assertThat(analysis.source()).isEqualTo(AnalysisSource.PLATFORM);
        assertThat(analysis.visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(analysis.stats()).isEqualTo(new RunStats(0, 0, 0, 0, null, Duration.ZERO));
        assertThat(analysis.createdAt()).isEqualTo(NOW);
        assertThat(analysis.owner()).isEqualTo(OWNER);
        assertThat(analysis.version()).isNull();
    }

    @Test
    void runningSetsTheStartAndTheRunnerRef() {
        Analysis analysis = service.running(queued(), LATER, "4242");

        assertThat(analysis.status()).isEqualTo(AnalysisStatus.RUNNING);
        assertThat(analysis.startedAt()).isEqualTo(LATER);
        assertThat(analysis.runnerRef()).isEqualTo("4242");
    }

    @Test
    void finishedRecordsTheEnd() {
        Analysis analysis = service.finished(queued(), AnalysisStatus.FAILED, LATER, "code", "message");

        assertThat(analysis.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.endedAt()).isEqualTo(LATER);
        assertThat(analysis.errorCode()).isEqualTo("code");
        assertThat(analysis.errorMessage()).isEqualTo("message");
    }

    @ParameterizedTest
    @EnumSource(
            value = AnalysisStatus.class,
            names = {"QUEUED", "RUNNING"})
    void finishedRejectsANonTerminalStatus(AnalysisStatus status) {
        Analysis analysis = queued();

        assertThatThrownBy(() -> service.finished(analysis, status, LATER, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Not a terminal status: " + status);
    }

    @Test
    void statsDecisionAndVisibilityCopiesKeepTheRest() {
        Analysis base = new Analysis(
                new AnalysisId("r_1"),
                Fakes.spec("NVDA"),
                AnalysisStatus.RUNNING,
                AnalysisSource.PLATFORM,
                null,
                null,
                new RunStats(0, 0, 0, 0, null, Duration.ZERO),
                NOW,
                NOW,
                null,
                null,
                null,
                null,
                "4242",
                OWNER,
                Visibility.PRIVATE,
                null,
                7L);
        RunStats stats = new RunStats(1, 2, 3, 4, null, Duration.ofSeconds(5));

        Analysis withStats = service.withStats(base, stats);
        Analysis withDecision = service.withDecision(base, null, "BUY");
        Analysis withVisibility = service.withVisibility(base, Visibility.PUBLIC, LATER);

        assertThat(withStats).isEqualTo(copyOf(base, stats, null, Visibility.PRIVATE, null));
        assertThat(withDecision.decision()).isEqualTo("BUY");
        assertThat(withDecision.version()).isEqualTo(7L);
        assertThat(withVisibility.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(withVisibility.visibilityChangedAt()).isEqualTo(LATER);
        assertThat(withVisibility.runnerRef()).isEqualTo("4242");
    }

    @Test
    void withVisibilityOfTakesTheVisibilityAndItsTimeOfTheOther() {
        Analysis shared = service.withVisibility(queued(), Visibility.PARTIAL, LATER);

        Analysis refreshed = service.withVisibilityOf(queued(), shared);

        assertThat(refreshed.visibility()).isEqualTo(Visibility.PARTIAL);
        assertThat(refreshed.visibilityChangedAt()).isEqualTo(LATER);
    }

    @Test
    void terminalStatusesAreTheEndedOnes() {
        assertThat(service.isTerminal(AnalysisStatus.QUEUED)).isFalse();
        assertThat(service.isTerminal(AnalysisStatus.RUNNING)).isFalse();
        assertThat(service.isTerminal(AnalysisStatus.COMPLETED)).isTrue();
        assertThat(service.isTerminal(AnalysisStatus.STOPPED)).isTrue();
        assertThat(service.isTerminal(AnalysisStatus.FAILED)).isTrue();
    }

    @Test
    void anOutcomeMapsToTheStatusOfTheSameName() {
        for (RunOutcome outcome : RunOutcome.values()) {
            assertThat(service.toStatus(outcome).name()).isEqualTo(outcome.name());
        }
    }

    @Test
    void importedWithoutARunSaysUnknownAndCutsTimesToMilliseconds() {
        ExternalAnalysis external = new ExternalAnalysis(
                ExternalAnalysis.Origin.REPORT_FILES,
                "nvda",
                LocalDate.of(2026, 9, 25),
                List.of(Analyst.MARKET),
                null,
                "BUY",
                null,
                NOW);

        Analysis analysis = service.imported(new AnalysisId("e_1"), null, OWNER, external, "report:NVDA/2026-09-25");

        assertThat(analysis.spec().ticker()).isEqualTo("NVDA");
        assertThat(analysis.spec().llmProvider()).isEqualTo("unknown");
        assertThat(analysis.spec().deepThinkLlm()).isEqualTo("unknown");
        assertThat(analysis.spec().outputLanguage()).isEqualTo("English");
        assertThat(analysis.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(analysis.source()).isEqualTo(AnalysisSource.EXTERNAL);
        assertThat(analysis.externalRef()).isEqualTo("report:NVDA/2026-09-25");
        assertThat(analysis.endedAt()).isEqualTo(Instant.parse("2026-10-01T05:06:07.123Z"));
        assertThat(analysis.startedAt()).isEqualTo(analysis.endedAt());
        assertThat(analysis.stats()).isEqualTo(new RunStats(0, 0, 0, 0, null, Duration.ZERO));
        assertThat(analysis.version()).isNull();
    }

    @Test
    void importedReportFilesWithoutADecisionFailAsIncomplete() {
        ExternalAnalysis external = new ExternalAnalysis(
                ExternalAnalysis.Origin.REPORT_FILES,
                "NVDA",
                LocalDate.of(2026, 9, 25),
                List.of(Analyst.MARKET),
                null,
                null,
                null,
                NOW);

        Analysis analysis = service.imported(new AnalysisId("e_1"), 3L, OWNER, external, "ref");

        assertThat(analysis.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.errorCode()).isEqualTo("incomplete_report");
        assertThat(analysis.version()).isEqualTo(3L);
    }

    @Test
    void importedRunHistoryTakesModelsStatsAndTimesFromTheRun() {
        RunStats stats = new RunStats(1, 2, 3, 4, null, Duration.ofSeconds(5));
        ExternalRun run = new ExternalRun(
                "run-1",
                AnalysisStatus.STOPPED,
                "stopped early",
                "deepseek",
                "deep",
                "quick",
                3,
                "Turkish",
                stats,
                NOW.minusSeconds(30),
                NOW);
        ExternalAnalysis external = new ExternalAnalysis(
                ExternalAnalysis.Origin.RUN_HISTORY,
                "NVDA",
                LocalDate.of(2026, 9, 25),
                List.of(Analyst.MARKET),
                null,
                null,
                run,
                NOW);

        Analysis analysis = service.imported(new AnalysisId("e_2"), null, OWNER, external, "run:run-1");

        assertThat(analysis.status()).isEqualTo(AnalysisStatus.STOPPED);
        assertThat(analysis.errorMessage()).isEqualTo("stopped early");
        assertThat(analysis.spec().llmProvider()).isEqualTo("deepseek");
        assertThat(analysis.spec().maxDebateRounds()).isEqualTo(3);
        assertThat(analysis.spec().maxRiskDiscussRounds()).isEqualTo(3);
        assertThat(analysis.spec().outputLanguage()).isEqualTo("Turkish");
        assertThat(analysis.stats()).isEqualTo(stats);
        assertThat(analysis.startedAt()).isEqualTo(Instant.parse("2026-10-01T05:05:37.123Z"));
    }

    private Analysis copyOf(
            Analysis base, RunStats stats, String decision, Visibility visibility, Instant visibilityChangedAt) {
        return new Analysis(
                base.id(),
                base.spec(),
                base.status(),
                base.source(),
                base.rating(),
                decision,
                stats,
                base.createdAt(),
                base.startedAt(),
                base.endedAt(),
                base.errorCode(),
                base.errorMessage(),
                base.externalRef(),
                base.runnerRef(),
                base.owner(),
                visibility,
                visibilityChangedAt,
                base.version());
    }
}
