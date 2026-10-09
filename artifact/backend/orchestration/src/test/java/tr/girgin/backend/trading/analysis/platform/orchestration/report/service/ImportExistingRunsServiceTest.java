package tr.girgin.backend.trading.analysis.platform.orchestration.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRegistration;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRegistration.Outcome;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.WatchDataDirUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSection;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportSource;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.RunHistoryEntry;
import tr.girgin.backend.trading.analysis.platform.orchestration.report.model.ImportResult;

class ImportExistingRunsServiceTest {

    private static final WatchDataDirUseCase NO_WATCH = _ -> () -> {};
    private static final Duration SETTLE_TIME = Duration.ofMinutes(10);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC);

    private final List<ExternalAnalysis> registered = new ArrayList<>();

    @Test
    void registersEveryReportWithWhatItsFilesTell() {
        Report goog = new Report(
                new ReportKey("GOOG", LocalDate.of(2026, 9, 28)),
                Map.of(
                        ReportSection.MARKET_REPORT,
                        "m",
                        ReportSection.SENTIMENT_REPORT,
                        "s",
                        ReportSection.FINAL_TRADE_DECISION,
                        "**Rating**: Hold"),
                Map.of(),
                Rating.HOLD,
                Set.of(ReportSource.REPORT_TREE),
                Instant.parse("2026-09-28T12:00:00Z"));
        Report mu = new Report(
                new ReportKey("MU", LocalDate.of(2026, 9, 27)),
                Map.of(ReportSection.NEWS_REPORT, "n"),
                Map.of(),
                null,
                Set.of(ReportSource.REPORT_TREE),
                Instant.parse("2026-09-27T12:00:00Z"));
        ImportExistingRunsService service = new ImportExistingRunsService(
                () -> List.of(goog, mu),
                List::of,
                external -> {
                    registered.add(external);
                    Outcome outcome = external.ticker().equals("GOOG") ? Outcome.CREATED : Outcome.UNCHANGED;
                    return new ExternalRegistration(Analysis.imported(AnalysisId.newId(), null, external), outcome);
                },
                NO_WATCH,
                false,
                false,
                SETTLE_TIME,
                CLOCK);

        ImportResult result = service.importExistingRuns();

        assertThat(result).isEqualTo(new ImportResult(2, 1, 0, 1));
        assertThat(registered.getFirst().analysts()).containsExactlyInAnyOrder(Analyst.MARKET, Analyst.SOCIAL);
        assertThat(registered.getFirst().rating())
                .isEqualTo(tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating.HOLD);
        assertThat(registered.getFirst().decision()).isEqualTo("**Rating**: Hold");
        assertThat(registered.get(1).decision()).isNull();
        assertThat(registered.get(1).rating()).isNull();
        assertThat(registered).allSatisfy(external -> assertThat(external.run()).isNull());
    }

    @Test
    void addsTheRunHistoryToReportsAndImportsRunsThatLeftNoReport() {
        ReportKey nvda = new ReportKey("NVDA", LocalDate.of(2026, 9, 27));
        Report report = new Report(
                nvda,
                Map.of(ReportSection.MARKET_REPORT, "m", ReportSection.FINAL_TRADE_DECISION, "**Rating**: Overweight"),
                Map.of(),
                Rating.OVERWEIGHT,
                Set.of(ReportSource.REPORT_TREE),
                Instant.parse("2026-09-27T21:34:35Z"));
        // Newest first, as the use case returns them.
        List<RunHistoryEntry> history = List.of(
                entry("a2cd", nvda, RunHistoryEntry.Status.COMPLETED, null, "2026-09-27T21:34:35Z"),
                entry(
                        "62ae",
                        nvda,
                        RunHistoryEntry.Status.FAILED,
                        "Run did not complete (server restart).",
                        "2026-09-27T21:11:06Z"),
                entry("old0", nvda, RunHistoryEntry.Status.COMPLETED, null, "2026-09-27T20:00:00Z"),
                entry(
                        "6d10",
                        new ReportKey("NVDIA", nvda.tradeDate()),
                        RunHistoryEntry.Status.STOPPED,
                        null,
                        "2026-09-27T20:55:22Z"));
        ImportExistingRunsService service = new ImportExistingRunsService(
                () -> List.of(report),
                () -> history,
                external -> {
                    registered.add(external);
                    return new ExternalRegistration(
                            Analysis.imported(AnalysisId.newId(), null, external), Outcome.CREATED);
                },
                NO_WATCH,
                false,
                false,
                SETTLE_TIME,
                CLOCK);

        assertThat(service.importExistingRuns()).isEqualTo(new ImportResult(3, 3, 0, 0));

        ExternalAnalysis fromReport = registered.getFirst();
        assertThat(fromReport.origin()).isEqualTo(ExternalAnalysis.Origin.REPORT_FILES);
        assertThat(fromReport.run().id()).isEqualTo("a2cd");
        assertThat(fromReport.run().llmProvider()).isEqualTo("deepseek");
        assertThat(fromReport.run().quickThinkLlm()).isEqualTo(Analysis.UNKNOWN);
        assertThat(fromReport.run().debateRounds()).isEqualTo(5);
        assertThat(fromReport.run().stats().costUsd()).isEqualByComparingTo("0.0277");
        assertThat(registered.subList(1, 3)).allSatisfy(external -> {
            assertThat(external.origin()).isEqualTo(ExternalAnalysis.Origin.RUN_HISTORY);
            assertThat(external.analysts()).containsExactly(Analyst.MARKET, Analyst.NEWS);
        });
        assertThat(registered.get(1).run().status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(registered.get(1).run().errorMessage()).isEqualTo("Run did not complete (server restart).");
        assertThat(registered.get(2).ticker()).isEqualTo("NVDIA");
        assertThat(registered.get(2).run().status()).isEqualTo(AnalysisStatus.STOPPED);
    }

    @Test
    void holdsBackReportsWithoutDecisionThatMayStillBeRunning() {
        Report running = new Report(
                new ReportKey("AMD", LocalDate.of(2026, 9, 30)),
                Map.of(ReportSection.MARKET_REPORT, "m"),
                Map.of(),
                null,
                Set.of(ReportSource.REPORT_TREE),
                CLOCK.instant().minus(Duration.ofMinutes(2)));
        Report crashed = new Report(
                new ReportKey("INTC", LocalDate.of(2026, 9, 30)),
                Map.of(ReportSection.MARKET_REPORT, "m"),
                Map.of(),
                null,
                Set.of(ReportSource.REPORT_TREE),
                CLOCK.instant().minus(Duration.ofMinutes(20)));
        Report finished = new Report(
                new ReportKey("NVDA", LocalDate.of(2026, 9, 30)),
                Map.of(ReportSection.FINAL_TRADE_DECISION, "**Rating**: Hold"),
                Map.of(),
                Rating.HOLD,
                Set.of(ReportSource.REPORT_TREE),
                CLOCK.instant().minus(Duration.ofMinutes(1)));
        ImportExistingRunsService service = new ImportExistingRunsService(
                () -> List.of(running, crashed, finished),
                List::of,
                external -> {
                    registered.add(external);
                    return new ExternalRegistration(
                            Analysis.imported(AnalysisId.newId(), null, external), Outcome.CREATED);
                },
                NO_WATCH,
                false,
                false,
                SETTLE_TIME,
                CLOCK);

        assertThat(service.importExistingRuns().scanned()).isEqualTo(2);
        assertThat(registered).extracting(ExternalAnalysis::ticker).containsExactly("INTC", "NVDA");
    }

    private static RunHistoryEntry entry(
            String id, ReportKey key, RunHistoryEntry.Status status, String error, String endedAt) {
        Instant ended = Instant.parse(endedAt);
        return new RunHistoryEntry(
                id,
                key,
                List.of("market", "news", "reddit"),
                status,
                error,
                "deepseek",
                "deepseek-v4-pro",
                null,
                5,
                null,
                10,
                0,
                68_794,
                28_916,
                new BigDecimal("0.0277"),
                Duration.ofMinutes(20),
                ended.minus(Duration.ofMinutes(20)),
                ended);
    }
}
