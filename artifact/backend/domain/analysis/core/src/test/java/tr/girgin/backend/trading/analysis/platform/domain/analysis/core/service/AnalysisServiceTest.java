package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisScope;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEventType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunOutcome;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunHandle;

class AnalysisServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T05:06:07Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private Fakes.Repository repository;
    private Fakes.Runner runner;
    private Fakes.EventStore eventStore;
    private AnalysisEventHub hub;
    private AnalysisService service;
    private AnalysisListService lists;

    @BeforeEach
    void setUp() {
        repository = new Fakes.Repository();
        runner = new Fakes.Runner();
        eventStore = new Fakes.EventStore();
        hub = new AnalysisEventHub(repository, eventStore);
        service = new AnalysisService(repository, runner, new Fakes.Credentials(), eventStore, hub, CLOCK, 2);
        lists = new AnalysisListService(repository);
    }

    private Analysis start(AnalysisSpec spec) {
        return service.start(spec, Fakes.OWNER);
    }

    private Analysis get(AnalysisId id) {
        return service.get(id, Fakes.OWN);
    }

    @Test
    void takesEveryTimeFromTheClock() {
        Analysis started = start(Fakes.spec("NVDA"));

        assertThat(started.createdAt()).isEqualTo(NOW);
        assertThat(started.startedAt()).isEqualTo(NOW);

        runner.sink(started.id()).onExit(137);

        assertThat(get(started.id()).endedAt()).isEqualTo(NOW);
        assertThat(eventStore.read(started.id(), 0))
                .singleElement()
                .satisfies(e -> assertThat(e.timestamp()).isEqualTo(NOW));
    }

    @Test
    void validatesTheTradeDateAgainstTheClocksDate() {
        LocalDate today = LocalDate.ofInstant(NOW, ZoneOffset.UTC);

        assertThat(start(Fakes.spec("NVDA", today)).status()).isEqualTo(AnalysisStatus.RUNNING);
        assertInvalid(Fakes.spec("MU", today.plusDays(1)), "tradeDate");
    }

    @Test
    void startsARunImmediatelyWhenASlotIsFree() {
        Analysis analysis = start(Fakes.spec("nvda"));

        assertThat(analysis.status()).isEqualTo(AnalysisStatus.RUNNING);
        assertThat(analysis.spec().ticker()).isEqualTo("NVDA");
        assertThat(analysis.startedAt()).isNotNull();
        assertThat(runner.sinks).containsKey(analysis.id());
        assertThat(runner.lastEnvironment).containsEntry("DEEPSEEK_API_KEY", "sk-test");
    }

    @Test
    void aRunBelongsToItsStarterAndKeepsItThroughItsTransitions() {
        AnalysisId id = start(Fakes.spec("NVDA")).id();

        assertThat(get(id).owner()).isEqualTo(Fakes.OWNER);

        runner.sink(id).onEvent(Fakes.stats(1, 7));
        runner.sink(id).onEvent(Fakes.finished(2, RunOutcome.COMPLETED, null));
        runner.sink(id).onExit(0);

        assertThat(get(id).owner()).isEqualTo(Fakes.OWNER);
    }

    @Test
    void queuesBeyondTheConcurrencyLimitAndStartsWhenASlotFrees() {
        Analysis first = start(Fakes.spec("NVDA"));
        start(Fakes.spec("MU"));
        Analysis third = start(Fakes.spec("GOOG"));

        assertThat(third.status()).isEqualTo(AnalysisStatus.QUEUED);
        assertThat(runner.sinks).hasSize(2);

        runner.sink(first.id()).onEvent(Fakes.finished(1, RunOutcome.COMPLETED, null));
        runner.sink(first.id()).onExit(0);

        assertThat(get(third.id()).status()).isEqualTo(AnalysisStatus.RUNNING);
        assertThat(runner.sinks).containsKey(third.id());
    }

    @Test
    void rejectsASecondActiveRunForTheSameTickerAndDate() {
        Analysis first = start(Fakes.spec("NVDA"));

        assertThatThrownBy(() -> start(Fakes.spec("nvda"))).isInstanceOfSatisfying(AnalysisException.class, e -> {
            assertThat(e.error()).isEqualTo(AnalysisError.ALREADY_RUNNING);
            assertThat(e.params()).containsEntry("id", first.id().value());
        });
    }

    @Test
    void allowsTheSameTickerAgainOnceTheFirstRunEnded() {
        Analysis first = start(Fakes.spec("NVDA"));
        runner.sink(first.id()).onEvent(Fakes.finished(1, RunOutcome.COMPLETED, null));
        runner.sink(first.id()).onExit(0);

        assertThat(start(Fakes.spec("NVDA")).status()).isEqualTo(AnalysisStatus.RUNNING);
    }

    @Test
    void rejectsInvalidSpecsWithTheOffendingField() {
        AnalysisSpec future = new AnalysisSpec(
                "NVDA",
                LocalDate.ofInstant(NOW, ZoneOffset.UTC).plusDays(1),
                AssetType.STOCK,
                List.of(Analyst.MARKET),
                "openai",
                "gpt",
                "gpt",
                1,
                1,
                "English",
                false);
        AnalysisSpec traversal = new AnalysisSpec(
                "../etc",
                LocalDate.of(2026, 1, 2),
                AssetType.STOCK,
                List.of(Analyst.MARKET),
                "openai",
                "gpt",
                "gpt",
                1,
                1,
                "English",
                false);
        AnalysisSpec injection = new AnalysisSpec(
                "NVDA",
                LocalDate.of(2026, 1, 2),
                AssetType.STOCK,
                List.of(Analyst.MARKET),
                "openai",
                "gpt; rm -rf /",
                "gpt",
                1,
                1,
                "English",
                false);
        AnalysisSpec noAnalysts = new AnalysisSpec(
                "NVDA",
                LocalDate.of(2026, 1, 2),
                AssetType.STOCK,
                List.of(),
                "openai",
                "gpt",
                "gpt",
                1,
                1,
                "English",
                false);

        assertInvalid(future, "tradeDate");
        assertInvalid(traversal, "ticker");
        assertInvalid(injection, "deepThinkLlm");
        assertInvalid(noAnalysts, "analysts");
        assertThat(repository.rows).isEmpty();
    }

    @Test
    void recordsStatsDecisionAndCompletion() {
        AnalysisId id = start(Fakes.spec("NVDA")).id();
        var sink = runner.sink(id);

        sink.onEvent(Fakes.event(1, RunEventType.RUN_STARTED));
        sink.onEvent(Fakes.stats(2, 7));
        sink.onEvent(Fakes.decision(3, Rating.OVERWEIGHT));
        sink.onEvent(Fakes.finished(4, RunOutcome.COMPLETED, null));
        sink.onExit(0);

        Analysis done = get(id);
        assertThat(done.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(done.stats().llmCalls()).isEqualTo(7);
        assertThat(done.rating()).isEqualTo(Rating.OVERWEIGHT);
        assertThat(done.decision()).isEqualTo("Rating: OVERWEIGHT");
        assertThat(done.endedAt()).isNotNull();
        assertThat(done.errorCode()).isNull();
    }

    @Test
    void reportsAStaleWriteAsConcurrentUpdate() {
        AnalysisId id = start(Fakes.spec("NVDA")).id();
        repository.failOnWrite = new OptimisticLockingFailureException("stale");

        assertThatThrownBy(() -> runner.sink(id).onEvent(Fakes.stats(1, 7)))
                .isInstanceOfSatisfying(AnalysisException.class, e -> {
                    assertThat(e.error()).isEqualTo(AnalysisError.CONCURRENT_UPDATE);
                    assertThat(e.params()).containsEntry("id", id.value());
                });
    }

    @Test
    void recordsARunnerErrorWithItsMessage() {
        AnalysisId id = start(Fakes.spec("NVDA")).id();

        runner.sink(id).onEvent(Fakes.finished(1, RunOutcome.FAILED, "provider exploded"));
        runner.sink(id).onExit(1);

        Analysis failed = get(id);
        assertThat(failed.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo(AnalysisService.RUNNER_ERROR);
        assertThat(failed.errorMessage()).isEqualTo("RuntimeError: provider exploded");
    }

    @Test
    void marksARunThatDiedWithoutReportingAsFailedAndAppendsTheEnd() {
        AnalysisId id = start(Fakes.spec("NVDA")).id();
        runner.sink(id).onEvent(Fakes.event(1, RunEventType.RUN_STARTED));

        runner.sink(id).onExit(137);

        Analysis died = get(id);
        assertThat(died.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(died.errorCode()).isEqualTo(AnalysisService.RUNNER_DIED);
        assertThat(eventStore.read(id, 0)).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(RunEventType.RUN_FINISHED);
            assertThat(e.seq()).isEqualTo(1);
            assertThat(e.payload()).containsEntry("error_code", AnalysisService.RUNNER_DIED);
        });
    }

    @Test
    void failsARunWhoseRunnerCannotStart() {
        runner.failOnStart = new IllegalStateException("python not found");

        Analysis analysis = start(Fakes.spec("NVDA"));

        assertThat(analysis.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.errorCode()).isEqualTo(AnalysisService.RUNNER_START_FAILED);
        assertThat(analysis.errorMessage()).isEqualTo("python not found");
    }

    @Test
    void stopsAQueuedRunAtOnce() {
        start(Fakes.spec("NVDA"));
        start(Fakes.spec("MU"));
        AnalysisId queued = start(Fakes.spec("GOOG")).id();

        Analysis stopped = service.stop(queued, Fakes.OWN);

        assertThat(stopped.status()).isEqualTo(AnalysisStatus.STOPPED);
        assertThat(runner.stopped).isEmpty();
    }

    @Test
    void asksTheRunnerToStopARunningRun() {
        AnalysisId id = start(Fakes.spec("NVDA")).id();

        service.stop(id, Fakes.OWN);

        assertThat(runner.stopped).extracting(h -> h.ref()).containsExactly("pid-" + id.value());
    }

    @Test
    void refusesToStopAFinishedRun() {
        AnalysisId id = start(Fakes.spec("NVDA")).id();
        runner.sink(id).onEvent(Fakes.finished(1, RunOutcome.COMPLETED, null));
        runner.sink(id).onExit(0);

        assertThatThrownBy(() -> service.stop(id, Fakes.OWN))
                .isInstanceOfSatisfying(
                        AnalysisException.class, e -> assertThat(e.error()).isEqualTo(AnalysisError.NOT_RUNNING));
    }

    @Test
    void rerunStartsANewRunWithTheSameSpec() {
        Analysis first = start(Fakes.spec("NVDA"));
        runner.sink(first.id()).onEvent(Fakes.finished(1, RunOutcome.COMPLETED, null));
        runner.sink(first.id()).onExit(0);

        Analysis again = service.rerun(first.id(), Fakes.OWN);

        assertThat(again.id()).isNotEqualTo(first.id());
        assertThat(again.spec()).isEqualTo(first.spec());
    }

    @Test
    void aRerunBelongsToTheUserWhoRanItAgain() {
        Analysis first = start(Fakes.spec("NVDA"));
        runner.sink(first.id()).onEvent(Fakes.finished(1, RunOutcome.COMPLETED, null));
        runner.sink(first.id()).onExit(0);

        Analysis again = service.rerun(first.id(), Fakes.EVERYONE);

        assertThat(again.owner()).isEqualTo(Fakes.OTHER);
        assertThat(get(first.id()).owner()).isEqualTo(Fakes.OWNER);
    }

    @Test
    void failsRunsLeftRunningWhoseRunnerIsGone() {
        Analysis orphan = orphan("NVDA", "4242");

        service.afterSingletonsInstantiated();

        Analysis failed = get(orphan.id());
        assertThat(failed.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo(AnalysisService.PLATFORM_RESTARTED);
        assertThat(eventStore.read(orphan.id(), 0))
                .last()
                .satisfies(e -> assertThat(e.type()).isEqualTo(RunEventType.RUN_FINISHED));
    }

    @Test
    void takesTheOutcomeOfRunsThatEndedWhileThePlatformWasDown() {
        Analysis orphan = orphan("NVDA", "4242");
        eventStore.append(orphan.id(), Fakes.stats(1, 12));
        eventStore.append(orphan.id(), Fakes.decision(2, Rating.OVERWEIGHT));
        eventStore.append(orphan.id(), Fakes.finished(3, RunOutcome.COMPLETED, null));

        service.afterSingletonsInstantiated();

        Analysis done = get(orphan.id());
        assertThat(done.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(done.rating()).isEqualTo(Rating.OVERWEIGHT);
        assertThat(done.stats().llmCalls()).isEqualTo(12);
        assertThat(runner.reattachedAfter).isEmpty();
    }

    @Test
    void followsRunsWhoseRunnerIsStillGoing() {
        Analysis orphan = orphan("NVDA", "4242");
        eventStore.append(orphan.id(), Fakes.stats(1, 3));
        runner.alive.add("4242");

        service.afterSingletonsInstantiated();

        assertThat(runner.reattachedAfter).containsEntry(orphan.id(), 1L);
        assertThat(get(orphan.id()).status()).isEqualTo(AnalysisStatus.RUNNING);
        assertThat(get(orphan.id()).stats().llmCalls()).isEqualTo(3);
        assertThatThrownBy(() -> start(Fakes.spec("NVDA")))
                .isInstanceOfSatisfying(
                        AnalysisException.class, e -> assertThat(e.error()).isEqualTo(AnalysisError.ALREADY_RUNNING));

        service.stop(orphan.id(), Fakes.OWN);
        assertThat(runner.stopped).extracting(RunHandle::ref).containsExactly("4242");

        runner.sink(orphan.id()).onEvent(Fakes.finished(2, RunOutcome.STOPPED, null));
        runner.sink(orphan.id()).onExit(-1);
        assertThat(get(orphan.id()).status()).isEqualTo(AnalysisStatus.STOPPED);
    }

    @Test
    void queuesRunsLeftQueuedAgainInTheirOrder() {
        Instant now = Instant.now();
        Analysis first = Analysis.queued(AnalysisId.newId(), Fakes.spec("NVDA"), Fakes.OWNER, now.minusSeconds(3));
        Analysis second = Analysis.queued(AnalysisId.newId(), Fakes.spec("MU"), Fakes.OWNER, now.minusSeconds(2));
        Analysis third = Analysis.queued(AnalysisId.newId(), Fakes.spec("GOOG"), Fakes.OWNER, now.minusSeconds(1));
        repository.insert(third);
        repository.insert(first);
        repository.insert(second);

        service.afterSingletonsInstantiated();

        assertThat(runner.sinks.keySet()).containsExactly(first.id(), second.id());
        assertThat(get(third.id()).status()).isEqualTo(AnalysisStatus.QUEUED);
    }

    private Analysis orphan(String ticker, String runnerRef) {
        Analysis orphan = Analysis.queued(AnalysisId.newId(), Fakes.spec(ticker), Fakes.OWNER, Instant.now())
                .running(Instant.now(), runnerRef);
        repository.insert(orphan);
        return orphan;
    }

    @Test
    void listsNewestFirstWithFilters() {
        start(Fakes.spec("NVDA"));
        start(Fakes.spec("MU"));

        assertThat(lists.list(AnalysisFilter.ALL, AnalysisScope.MINE, Fakes.OWN))
                .hasSize(2);
        assertThat(lists.list(new AnalysisFilter(null, "MU", null), AnalysisScope.MINE, Fakes.OWN))
                .singleElement()
                .satisfies(a -> assertThat(a.spec().ticker()).isEqualTo("MU"));
    }

    @Test
    void listsOnlyTheCallersRunsInTheMineScope() {
        Analysis mine = start(Fakes.spec("NVDA"));
        service.start(Fakes.spec("MU"), Fakes.OTHER);

        assertThat(lists.list(AnalysisFilter.ALL, AnalysisScope.MINE, Fakes.OWN))
                .extracting(Analysis::id)
                .containsExactly(mine.id());
        assertThat(lists.list(new AnalysisFilter(null, null, Fakes.OTHER), AnalysisScope.MINE, Fakes.OWN))
                .extracting(Analysis::id)
                .containsExactly(mine.id());
        assertThat(lists.list(AnalysisFilter.ALL, AnalysisScope.MINE, Fakes.EVERYONE))
                .extracting(Analysis::owner)
                .containsExactly(Fakes.OTHER);
    }

    @Test
    void listsEveryonesRunsInTheAllScopeWithAllUsers() {
        start(Fakes.spec("NVDA"));
        service.start(Fakes.spec("MU"), Fakes.OTHER);
        service.start(Fakes.spec("GOOG"), Fakes.OTHER);

        assertThat(lists.list(AnalysisFilter.ALL, AnalysisScope.ALL, Fakes.EVERYONE))
                .hasSize(3);
        assertThat(lists.list(new AnalysisFilter(null, null, Fakes.OWNER), AnalysisScope.ALL, Fakes.EVERYONE))
                .singleElement()
                .satisfies(a -> assertThat(a.owner()).isEqualTo(Fakes.OWNER));
    }

    @Test
    void theAllScopeIsTheMineScopeWithoutAllUsers() {
        Analysis mine = start(Fakes.spec("NVDA"));
        service.start(Fakes.spec("MU"), Fakes.OTHER);

        assertThat(lists.list(AnalysisFilter.ALL, AnalysisScope.ALL, Fakes.OWN))
                .extracting(Analysis::id)
                .containsExactly(mine.id());
    }

    @Test
    void theSharedScopeHoldsOtherUsersPublicAndPartialRunsOnly() {
        Analysis ownPublic = publicRun("NVDA", Fakes.OWNER);
        Analysis othersPublic = publicRun("MU", Fakes.OTHER);
        Analysis othersPartial = sharedRun("AMD", Fakes.OTHER, Visibility.PARTIAL);
        Analysis othersPrivate = service.start(Fakes.spec("GOOG"), Fakes.OTHER);

        assertThat(lists.list(AnalysisFilter.ALL, AnalysisScope.SHARED, Fakes.OWN))
                .extracting(Analysis::id)
                .containsExactlyInAnyOrder(othersPublic.id(), othersPartial.id());
        assertThat(lists.list(AnalysisFilter.ALL, AnalysisScope.SHARED, Fakes.OTHERS))
                .extracting(Analysis::id)
                .containsExactly(ownPublic.id())
                .doesNotContain(othersPrivate.id());
        assertThat(lists.list(AnalysisFilter.ALL, AnalysisScope.SHARED, Fakes.EVERYONE))
                .extracting(Analysis::id)
                .containsExactly(ownPublic.id());
    }

    @Test
    void statusAndTickerApplyToEveryScope() {
        publicRun("NVDA", Fakes.OWNER);
        publicRun("MU", Fakes.OWNER);

        for (AnalysisScope scope : AnalysisScope.values()) {
            AnalysisAccess access = scope == AnalysisScope.SHARED ? Fakes.OTHERS : Fakes.OWN;
            assertThat(lists.list(new AnalysisFilter(null, "MU", null), scope, access))
                    .singleElement()
                    .satisfies(a -> assertThat(a.spec().ticker()).isEqualTo("MU"));
            assertThat(lists.list(new AnalysisFilter(AnalysisStatus.COMPLETED, null, null), scope, access))
                    .hasSize(2);
            assertThat(lists.list(new AnalysisFilter(AnalysisStatus.RUNNING, null, null), scope, access))
                    .isEmpty();
        }
    }

    @Test
    void aQueuedRunIsPrivate() {
        assertThat(start(Fakes.spec("NVDA")).visibility()).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void anotherUsersPublicOrPartialRunCanBeReadButNotStoppedOrRunAgain() {
        for (Visibility visibility : List.of(Visibility.PUBLIC, Visibility.PARTIAL)) {
            Analysis shared = sharedRun("NVDA" + visibility, Fakes.OWNER, visibility);

            assertThat(service.get(shared.id(), Fakes.OTHERS).id()).isEqualTo(shared.id());
            assertNotFound(() -> service.stop(shared.id(), Fakes.OTHERS), shared.id());
            assertNotFound(() -> service.rerun(shared.id(), Fakes.OTHERS), shared.id());
        }
    }

    @Test
    void aRerunOfAPublicRunIsPrivate() {
        Analysis shared = publicRun("NVDA", Fakes.OWNER);

        assertThat(service.rerun(shared.id(), Fakes.OWN).visibility()).isEqualTo(Visibility.PRIVATE);
    }

    private Analysis publicRun(String ticker, OwnerId owner) {
        return sharedRun(ticker, owner, Visibility.PUBLIC);
    }

    private Analysis sharedRun(String ticker, OwnerId owner, Visibility visibility) {
        Analysis run = service.start(Fakes.spec(ticker), owner);
        runner.sink(run.id()).onEvent(Fakes.finished(1, RunOutcome.COMPLETED, null));
        runner.sink(run.id()).onExit(0);
        Analysis shared = repository.rows.get(run.id()).withVisibility(visibility, NOW);
        repository.rows.put(shared.id(), shared);
        return shared;
    }

    @Test
    void anotherUsersRunIsNotFoundForGetStopAndRerun() {
        AnalysisId id = start(Fakes.spec("NVDA")).id();

        assertNotFound(() -> service.get(id, Fakes.OTHERS), id);
        assertNotFound(() -> service.stop(id, Fakes.OTHERS), id);
        assertNotFound(() -> service.rerun(id, Fakes.OTHERS), id);
        assertThat(runner.stopped).isEmpty();
        assertThat(repository.rows).hasSize(1);
    }

    @Test
    void aRunWithoutAnOwnerIsNotFoundForUsersWithoutAllUsers() {
        Analysis unowned = Analysis.queued(AnalysisId.newId(), Fakes.spec("NVDA"), null, NOW);
        repository.insert(unowned);

        assertNotFound(() -> service.get(unowned.id(), Fakes.OWN), unowned.id());
        assertThat(service.get(unowned.id(), Fakes.EVERYONE).id()).isEqualTo(unowned.id());
    }

    @Test
    void allUsersMayGetStopAndRerunAnotherUsersRun() {
        AnalysisId id = start(Fakes.spec("NVDA")).id();

        assertThat(service.get(id, Fakes.EVERYONE).id()).isEqualTo(id);
        service.stop(id, Fakes.EVERYONE);
        assertThat(runner.stopped).hasSize(1);
        runner.sink(id).onEvent(Fakes.finished(1, RunOutcome.STOPPED, null));
        runner.sink(id).onExit(0);
        assertThat(service.rerun(id, Fakes.EVERYONE).owner()).isEqualTo(Fakes.OTHER);
    }

    @Test
    void assignUnownedTouchesOnlyAnalysesWithoutAnOwner() {
        Analysis owned = start(Fakes.spec("NVDA"));
        Analysis first = Analysis.queued(AnalysisId.newId(), Fakes.spec("MU"), null, NOW);
        Analysis second = Analysis.queued(AnalysisId.newId(), Fakes.spec("GOOG"), null, NOW);
        repository.insert(first);
        repository.insert(second);
        OwnerId admin = new OwnerId("admin");

        assertThat(service.assignUnowned(admin)).isEqualTo(2);

        assertThat(repository.rows.get(owned.id()).owner()).isEqualTo(Fakes.OWNER);
        assertThat(repository.rows.get(first.id()).owner()).isEqualTo(admin);
        assertThat(repository.rows.get(second.id()).owner()).isEqualTo(admin);
        assertThat(service.assignUnowned(admin)).isZero();
    }

    @Test
    void unknownIdsAreNotFound() {
        AnalysisId id = new AnalysisId("r_missing");

        assertNotFound(() -> service.get(id, Fakes.OWN), id);
    }

    private void assertNotFound(Runnable call, AnalysisId id) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(AnalysisException.class, e -> {
            assertThat(e.error()).isEqualTo(AnalysisError.NOT_FOUND);
            assertThat(e.getMessage()).isEqualTo("Analysis not found: " + id);
        });
    }

    private void assertInvalid(AnalysisSpec spec, String field) {
        assertThatThrownBy(() -> start(spec)).isInstanceOfSatisfying(AnalysisException.class, e -> {
            assertThat(e.error()).isEqualTo(AnalysisError.INVALID_SPEC);
            assertThat(e.params()).containsEntry("field", field);
        });
    }
}
