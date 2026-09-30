package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSource;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRegistration.Outcome;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRun;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunStats;

class ExternalAnalysisServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 28);
    private static final Instant FINISHED = Instant.parse("2026-09-28T12:00:00Z");
    private static final Instant STARTED = Instant.parse("2026-09-28T11:30:00.123Z");

    private final Fakes.Repository repository = new Fakes.Repository();
    private final ExternalAnalysisService service = new ExternalAnalysisService(repository);

    @Test
    void importsACompletedRun() {
        var registration = service.register(external("GOOG", Rating.HOLD, "**Rating**: Hold", FINISHED));

        Analysis analysis = registration.analysis();
        assertThat(registration.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(analysis.source()).isEqualTo(AnalysisSource.EXTERNAL);
        assertThat(analysis.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(analysis.rating()).isEqualTo(Rating.HOLD);
        assertThat(analysis.spec().llmProvider()).isEqualTo(Analysis.UNKNOWN);
        assertThat(analysis.spec().analysts()).containsExactly(Analyst.MARKET, Analyst.NEWS);
        assertThat(analysis.endedAt()).isEqualTo(FINISHED);
    }

    @Test
    void importsARunWithoutDecisionAsIncomplete() {
        Analysis analysis = service.register(external("NVDA", null, null, FINISHED)).analysis();

        assertThat(analysis.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.errorCode()).isEqualTo(Analysis.INCOMPLETE_REPORT);
    }

    @Test
    void isIdempotentAndFollowsChangedFiles() {
        service.register(external("GOOG", null, null, FINISHED));

        assertThat(service.register(external("GOOG", null, null, FINISHED)).outcome()).isEqualTo(Outcome.UNCHANGED);
        var updated = service.register(external("GOOG", Rating.SELL, "Sell", FINISHED.plusSeconds(60)));

        assertThat(updated.outcome()).isEqualTo(Outcome.UPDATED);
        assertThat(repository.findAll(AnalysisFilter.ALL)).singleElement()
                .satisfies(a -> assertThat(a.status()).isEqualTo(AnalysisStatus.COMPLETED));
    }

    @Test
    void leavesTickersAndDatesThePlatformRanAlone() {
        Analysis platformRun = Analysis.queued(AnalysisId.newId(), Fakes.spec("MU", DATE), Instant.now());
        repository.insert(platformRun);

        var registration = service.register(external("MU", Rating.BUY, "Buy", FINISHED));

        assertThat(registration.outcome()).isEqualTo(Outcome.UNCHANGED);
        assertThat(registration.analysis().id()).isEqualTo(platformRun.id());
        assertThat(repository.findAll(AnalysisFilter.ALL)).hasSize(1);
    }

    @Test
    void takesModelsUsageAndTimesFromTheRunHistory() {
        ExternalRun run = run("a2cd", AnalysisStatus.COMPLETED, null);

        Analysis analysis = service.register(ExternalAnalysis.reportFiles("GOOG", DATE,
                List.of(Analyst.MARKET), Rating.HOLD, "Hold", run, FINISHED)).analysis();

        assertThat(analysis.spec().llmProvider()).isEqualTo("deepseek");
        assertThat(analysis.spec().deepThinkLlm()).isEqualTo("deepseek-v4-pro");
        assertThat(analysis.spec().maxDebateRounds()).isEqualTo(3);
        assertThat(analysis.spec().outputLanguage()).isEqualTo("Turkish");
        assertThat(analysis.stats()).isEqualTo(run.stats());
        assertThat(analysis.startedAt()).isEqualTo(STARTED);
        assertThat(analysis.createdAt()).isEqualTo(STARTED);
        assertThat(analysis.endedAt()).isEqualTo(FINISHED);
        assertThat(analysis.externalRef()).isEqualTo("report:GOOG/" + DATE);
    }

    @Test
    void importsEachFailedRunOfTheHistoryOnItsOwn() {
        service.register(external("NVDA", Rating.BUY, "Buy", FINISHED));
        var failed = service.register(ExternalAnalysis.runHistory("NVDA", DATE, List.of(Analyst.MARKET),
                run("6d10", AnalysisStatus.FAILED, "OpenAIError: Missing credentials.")));
        service.register(ExternalAnalysis.runHistory("NVDA", DATE, List.of(Analyst.MARKET),
                run("83b0", AnalysisStatus.FAILED, "Run did not complete (server restart).")));

        assertThat(failed.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(failed.analysis().status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failed.analysis().errorCode()).isNull();
        assertThat(failed.analysis().errorMessage()).isEqualTo("OpenAIError: Missing credentials.");
        assertThat(failed.analysis().externalRef()).isEqualTo("run:6d10");
        assertThat(repository.findAll(AnalysisFilter.ALL)).hasSize(3);
        assertThat(service.register(ExternalAnalysis.runHistory("NVDA", DATE, List.of(Analyst.MARKET),
                run("6d10", AnalysisStatus.FAILED, "OpenAIError: Missing credentials."))).outcome())
                .isEqualTo(Outcome.UNCHANGED);
    }

    @Test
    void importsFailedRunsEvenWhenThePlatformRanTheTickerAndDate() {
        repository.insert(Analysis.queued(AnalysisId.newId(), Fakes.spec("MU", DATE), Instant.now()));

        var failed = service.register(ExternalAnalysis.runHistory("MU", DATE, List.of(Analyst.MARKET),
                run("fe90", AnalysisStatus.FAILED, "boom")));

        assertThat(failed.outcome()).isEqualTo(Outcome.CREATED);
    }

    private static ExternalRun run(String id, AnalysisStatus status, String error) {
        return new ExternalRun(id, status, error, "deepseek", "deepseek-v4-pro", "deepseek-v4-flash", 3,
                "Turkish", new RunStats(11, 0, 93_557, 29_480, new BigDecimal("0.0318"), Duration.ofMillis(1_582_790)),
                STARTED, FINISHED);
    }

    private static ExternalAnalysis external(String ticker, Rating rating, String decision, Instant finishedAt) {
        return ExternalAnalysis.reportFiles(ticker, DATE, List.of(Analyst.NEWS, Analyst.MARKET), rating, decision,
                null, finishedAt);
    }
}
