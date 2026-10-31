package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSource;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRegistration;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRegistration.Outcome;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRun;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunStats;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;

class ExternalAnalysisServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 28);
    private static final Instant FINISHED = Instant.parse("2026-09-28T12:00:00Z");
    private static final Instant STARTED = Instant.parse("2026-09-28T11:30:00.123Z");

    private final Fakes.Repository repository = new Fakes.Repository();
    private final ExternalAnalysisService service = new ExternalAnalysisService(
            repository, new AnalysisTransitionService(new AnalysisSpecService()), new AnalysisIdService());

    private ExternalRegistration register(ExternalAnalysis external) {
        return service.register(external, Fakes.OWNER);
    }

    @Test
    void aNewImportGetsTheGivenOwnerAndARefreshKeepsTheStoredOne() {
        Analysis created = service.register(external("GOOG", null, null, FINISHED), Fakes.OTHER)
                .analysis();
        assertThat(created.owner()).isEqualTo(Fakes.OTHER);

        Analysis refreshed = service.register(
                        external("GOOG", Rating.SELL, "Sell", FINISHED.plusSeconds(60)), Fakes.OWNER)
                .analysis();

        assertThat(refreshed.id()).isEqualTo(created.id());
        assertThat(refreshed.owner()).isEqualTo(Fakes.OTHER);
    }

    @Test
    void aNewImportMayHaveNoOwnerYet() {
        assertThat(service.register(external("GOOG", null, null, FINISHED), null)
                        .analysis()
                        .owner())
                .isNull();
    }

    @Test
    void aRefreshGivesAStoredUnownedImportTheGivenOwner() {
        Analysis created =
                service.register(external("GOOG", null, null, FINISHED), null).analysis();

        var refreshed = register(external("GOOG", null, null, FINISHED));

        assertThat(refreshed.outcome()).isEqualTo(Outcome.UPDATED);
        assertThat(refreshed.analysis().id()).isEqualTo(created.id());
        assertThat(refreshed.analysis().owner()).isEqualTo(Fakes.OWNER);
    }

    @Test
    void importsACompletedRun() {
        var registration = register(external("GOOG", Rating.HOLD, "**Rating**: Hold", FINISHED));

        Analysis analysis = registration.analysis();
        assertThat(registration.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(analysis.source()).isEqualTo(AnalysisSource.EXTERNAL);
        assertThat(analysis.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(analysis.rating()).isEqualTo(Rating.HOLD);
        assertThat(analysis.spec().llmProvider()).isEqualTo(AnalysisTransitionService.UNKNOWN);
        assertThat(analysis.spec().analysts()).containsExactly(Analyst.MARKET, Analyst.NEWS);
        assertThat(analysis.endedAt()).isEqualTo(FINISHED);
    }

    @Test
    void importsARunWithoutDecisionAsIncomplete() {
        Analysis analysis = register(external("NVDA", null, null, FINISHED)).analysis();

        assertThat(analysis.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.errorCode()).isEqualTo(AnalysisTransitionService.INCOMPLETE_REPORT);
    }

    @Test
    void isIdempotentAndFollowsChangedFiles() {
        register(external("GOOG", null, null, FINISHED));

        assertThat(register(external("GOOG", null, null, FINISHED)).outcome()).isEqualTo(Outcome.UNCHANGED);
        var updated = register(external("GOOG", Rating.SELL, "Sell", FINISHED.plusSeconds(60)));

        assertThat(updated.outcome()).isEqualTo(Outcome.UPDATED);
        assertThat(repository.findAll(Fakes.ALL))
                .singleElement()
                .satisfies(a -> assertThat(a.status()).isEqualTo(AnalysisStatus.COMPLETED));
    }

    @Test
    void carriesTheStoredVersionSoUnchangedFilesStayUnchanged() {
        ExternalAnalysis files = external("GOOG", Rating.HOLD, "Hold", FINISHED);
        Analysis stored = Fakes.TRANSITIONS.imported(Fakes.newId(), 3L, Fakes.OWNER, files, "report:GOOG/" + DATE);
        repository.rows.put(stored.id(), stored);

        var registration = register(files);

        assertThat(registration.outcome()).isEqualTo(Outcome.UNCHANGED);
        assertThat(registration.analysis()).isEqualTo(stored);
    }

    @Test
    void aRescanOfAnUnchangedPublicImportIsUnchangedAndWritesNothing() {
        Analysis created =
                register(external("GOOG", Rating.HOLD, "Hold", FINISHED)).analysis();
        Analysis shared = Fakes.withVisibility(created, Visibility.PUBLIC, FINISHED.plusSeconds(5));
        repository.rows.put(shared.id(), shared);
        repository.failOnWrite = new OptimisticLockingFailureException("no write expected");

        var registration = register(external("GOOG", Rating.HOLD, "Hold", FINISHED));

        assertThat(registration.outcome()).isEqualTo(Outcome.UNCHANGED);
        assertThat(registration.analysis().visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(registration.analysis().visibilityChangedAt()).isEqualTo(FINISHED.plusSeconds(5));
    }

    @Test
    void changedFilesUpdateAPublicImportAndKeepItPublic() {
        Analysis created = register(external("GOOG", null, null, FINISHED)).analysis();
        Analysis shared = Fakes.withVisibility(created, Visibility.PUBLIC, FINISHED.plusSeconds(5));
        repository.rows.put(shared.id(), shared);

        var registration = register(external("GOOG", Rating.SELL, "Sell", FINISHED.plusSeconds(60)));

        assertThat(registration.outcome()).isEqualTo(Outcome.UPDATED);
        assertThat(registration.analysis().rating()).isEqualTo(Rating.SELL);
        assertThat(registration.analysis().visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(registration.analysis().visibilityChangedAt()).isEqualTo(FINISHED.plusSeconds(5));
    }

    @Test
    void reportsAStaleReplaceAsConcurrentUpdate() {
        register(external("GOOG", null, null, FINISHED));
        repository.failOnWrite = new OptimisticLockingFailureException("stale");

        assertThatThrownBy(() -> register(external("GOOG", Rating.SELL, "Sell", FINISHED.plusSeconds(60))))
                .isInstanceOfSatisfying(
                        AnalysisException.class, e -> assertThat(e.error()).isEqualTo(AnalysisError.CONCURRENT_UPDATE));
    }

    @Test
    void leavesTickersAndDatesThePlatformRanAlone() {
        Analysis platformRun = Fakes.queued(Fakes.spec("MU", DATE), Fakes.OWNER, Instant.now());
        repository.insert(platformRun);

        var registration = register(external("MU", Rating.BUY, "Buy", FINISHED));

        assertThat(registration.outcome()).isEqualTo(Outcome.UNCHANGED);
        assertThat(registration.analysis().id()).isEqualTo(platformRun.id());
        assertThat(repository.findAll(Fakes.ALL)).hasSize(1);
    }

    @Test
    void takesModelsUsageAndTimesFromTheRunHistory() {
        ExternalRun run = run("a2cd", AnalysisStatus.COMPLETED, null);

        Analysis analysis = register(new ExternalAnalysis(
                        ExternalAnalysis.Origin.REPORT_FILES,
                        "GOOG",
                        DATE,
                        List.of(Analyst.MARKET),
                        Rating.HOLD,
                        "Hold",
                        run,
                        FINISHED))
                .analysis();

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
        register(external("NVDA", Rating.BUY, "Buy", FINISHED));
        var failed = register(runHistory(
                "NVDA",
                DATE,
                List.of(Analyst.MARKET),
                run("6d10", AnalysisStatus.FAILED, "OpenAIError: Missing credentials.")));
        register(runHistory(
                "NVDA",
                DATE,
                List.of(Analyst.MARKET),
                run("83b0", AnalysisStatus.FAILED, "Run did not complete (server restart).")));

        assertThat(failed.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(failed.analysis().status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failed.analysis().errorCode()).isNull();
        assertThat(failed.analysis().errorMessage()).isEqualTo("OpenAIError: Missing credentials.");
        assertThat(failed.analysis().externalRef()).isEqualTo("run:6d10");
        assertThat(repository.findAll(Fakes.ALL)).hasSize(3);
        assertThat(register(runHistory(
                                "NVDA",
                                DATE,
                                List.of(Analyst.MARKET),
                                run("6d10", AnalysisStatus.FAILED, "OpenAIError: Missing credentials.")))
                        .outcome())
                .isEqualTo(Outcome.UNCHANGED);
    }

    @Test
    void importsFailedRunsEvenWhenThePlatformRanTheTickerAndDate() {
        repository.insert(Fakes.queued(Fakes.spec("MU", DATE), Fakes.OWNER, Instant.now()));

        var failed =
                register(runHistory("MU", DATE, List.of(Analyst.MARKET), run("fe90", AnalysisStatus.FAILED, "boom")));

        assertThat(failed.outcome()).isEqualTo(Outcome.CREATED);
    }

    private static ExternalRun run(String id, AnalysisStatus status, String error) {
        return new ExternalRun(
                id,
                status,
                error,
                "deepseek",
                "deepseek-v4-pro",
                "deepseek-v4-flash",
                3,
                "Turkish",
                new RunStats(11, 0, 93_557, 29_480, new BigDecimal("0.0318"), Duration.ofMillis(1_582_790)),
                STARTED,
                FINISHED);
    }

    private static ExternalAnalysis external(String ticker, Rating rating, String decision, Instant finishedAt) {
        return new ExternalAnalysis(
                ExternalAnalysis.Origin.REPORT_FILES,
                ticker,
                DATE,
                List.of(Analyst.NEWS, Analyst.MARKET),
                rating,
                decision,
                null,
                finishedAt);
    }

    private static ExternalAnalysis runHistory(String ticker, LocalDate date, List<Analyst> analysts, ExternalRun run) {
        return new ExternalAnalysis(
                ExternalAnalysis.Origin.RUN_HISTORY, ticker, date, analysts, null, null, run, run.endedAt());
    }
}
