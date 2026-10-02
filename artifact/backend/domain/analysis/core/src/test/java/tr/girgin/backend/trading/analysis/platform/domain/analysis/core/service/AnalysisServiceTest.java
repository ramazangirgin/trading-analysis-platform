package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEventType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunOutcome;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunHandle;

class AnalysisServiceTest {

    private Fakes.Repository repository;
    private Fakes.Runner runner;
    private Fakes.EventStore eventStore;
    private AnalysisEventHub hub;
    private AnalysisService service;

    @BeforeEach
    void setUp() {
        repository = new Fakes.Repository();
        runner = new Fakes.Runner();
        eventStore = new Fakes.EventStore();
        hub = new AnalysisEventHub(repository, eventStore);
        service = new AnalysisService(repository, runner, new Fakes.Credentials(), eventStore, hub, 2);
    }

    @Test
    void startsARunImmediatelyWhenASlotIsFree() {
        Analysis analysis = service.start(Fakes.spec("nvda"));

        assertThat(analysis.status()).isEqualTo(AnalysisStatus.RUNNING);
        assertThat(analysis.spec().ticker()).isEqualTo("NVDA");
        assertThat(analysis.startedAt()).isNotNull();
        assertThat(runner.sinks).containsKey(analysis.id());
        assertThat(runner.lastEnvironment).containsEntry("DEEPSEEK_API_KEY", "sk-test");
    }

    @Test
    void queuesBeyondTheConcurrencyLimitAndStartsWhenASlotFrees() {
        Analysis first = service.start(Fakes.spec("NVDA"));
        service.start(Fakes.spec("MU"));
        Analysis third = service.start(Fakes.spec("GOOG"));

        assertThat(third.status()).isEqualTo(AnalysisStatus.QUEUED);
        assertThat(runner.sinks).hasSize(2);

        runner.sink(first.id()).onEvent(Fakes.finished(1, RunOutcome.COMPLETED, null));
        runner.sink(first.id()).onExit(0);

        assertThat(service.get(third.id()).status()).isEqualTo(AnalysisStatus.RUNNING);
        assertThat(runner.sinks).containsKey(third.id());
    }

    @Test
    void rejectsASecondActiveRunForTheSameTickerAndDate() {
        Analysis first = service.start(Fakes.spec("NVDA"));

        assertThatThrownBy(() -> service.start(Fakes.spec("nvda")))
                .isInstanceOfSatisfying(AnalysisException.class, e -> {
                    assertThat(e.error()).isEqualTo(AnalysisError.ALREADY_RUNNING);
                    assertThat(e.params()).containsEntry("id", first.id().value());
                });
    }

    @Test
    void allowsTheSameTickerAgainOnceTheFirstRunEnded() {
        Analysis first = service.start(Fakes.spec("NVDA"));
        runner.sink(first.id()).onEvent(Fakes.finished(1, RunOutcome.COMPLETED, null));
        runner.sink(first.id()).onExit(0);

        assertThat(service.start(Fakes.spec("NVDA")).status()).isEqualTo(AnalysisStatus.RUNNING);
    }

    @Test
    void rejectsInvalidSpecsWithTheOffendingField() {
        AnalysisSpec future = new AnalysisSpec("NVDA", LocalDate.now().plusDays(1), AssetType.STOCK,
                List.of(Analyst.MARKET), "openai", "gpt", "gpt", 1, 1, "English", false);
        AnalysisSpec traversal = new AnalysisSpec("../etc", LocalDate.of(2026, 1, 2), AssetType.STOCK,
                List.of(Analyst.MARKET), "openai", "gpt", "gpt", 1, 1, "English", false);
        AnalysisSpec injection = new AnalysisSpec("NVDA", LocalDate.of(2026, 1, 2), AssetType.STOCK,
                List.of(Analyst.MARKET), "openai", "gpt; rm -rf /", "gpt", 1, 1, "English", false);
        AnalysisSpec noAnalysts = new AnalysisSpec("NVDA", LocalDate.of(2026, 1, 2), AssetType.STOCK,
                List.of(), "openai", "gpt", "gpt", 1, 1, "English", false);

        assertInvalid(future, "tradeDate");
        assertInvalid(traversal, "ticker");
        assertInvalid(injection, "deepThinkLlm");
        assertInvalid(noAnalysts, "analysts");
        assertThat(repository.rows).isEmpty();
    }

    @Test
    void recordsStatsDecisionAndCompletion() {
        AnalysisId id = service.start(Fakes.spec("NVDA")).id();
        var sink = runner.sink(id);

        sink.onEvent(Fakes.event(1, RunEventType.RUN_STARTED));
        sink.onEvent(Fakes.stats(2, 7));
        sink.onEvent(Fakes.decision(3, Rating.OVERWEIGHT));
        sink.onEvent(Fakes.finished(4, RunOutcome.COMPLETED, null));
        sink.onExit(0);

        Analysis done = service.get(id);
        assertThat(done.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(done.stats().llmCalls()).isEqualTo(7);
        assertThat(done.rating()).isEqualTo(Rating.OVERWEIGHT);
        assertThat(done.decision()).isEqualTo("Rating: OVERWEIGHT");
        assertThat(done.endedAt()).isNotNull();
        assertThat(done.errorCode()).isNull();
    }

    @Test
    void recordsARunnerErrorWithItsMessage() {
        AnalysisId id = service.start(Fakes.spec("NVDA")).id();

        runner.sink(id).onEvent(Fakes.finished(1, RunOutcome.FAILED, "provider exploded"));
        runner.sink(id).onExit(1);

        Analysis failed = service.get(id);
        assertThat(failed.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo(AnalysisService.RUNNER_ERROR);
        assertThat(failed.errorMessage()).isEqualTo("RuntimeError: provider exploded");
    }

    @Test
    void marksARunThatDiedWithoutReportingAsFailedAndAppendsTheEnd() {
        AnalysisId id = service.start(Fakes.spec("NVDA")).id();
        runner.sink(id).onEvent(Fakes.event(1, RunEventType.RUN_STARTED));

        runner.sink(id).onExit(137);

        Analysis died = service.get(id);
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

        Analysis analysis = service.start(Fakes.spec("NVDA"));

        assertThat(analysis.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.errorCode()).isEqualTo(AnalysisService.RUNNER_START_FAILED);
        assertThat(analysis.errorMessage()).isEqualTo("python not found");
    }

    @Test
    void stopsAQueuedRunAtOnce() {
        service.start(Fakes.spec("NVDA"));
        service.start(Fakes.spec("MU"));
        AnalysisId queued = service.start(Fakes.spec("GOOG")).id();

        Analysis stopped = service.stop(queued);

        assertThat(stopped.status()).isEqualTo(AnalysisStatus.STOPPED);
        assertThat(runner.stopped).isEmpty();
    }

    @Test
    void asksTheRunnerToStopARunningRun() {
        AnalysisId id = service.start(Fakes.spec("NVDA")).id();

        service.stop(id);

        assertThat(runner.stopped).extracting(h -> h.ref()).containsExactly("pid-" + id.value());
    }

    @Test
    void refusesToStopAFinishedRun() {
        AnalysisId id = service.start(Fakes.spec("NVDA")).id();
        runner.sink(id).onEvent(Fakes.finished(1, RunOutcome.COMPLETED, null));
        runner.sink(id).onExit(0);

        assertThatThrownBy(() -> service.stop(id))
                .isInstanceOfSatisfying(AnalysisException.class,
                        e -> assertThat(e.error()).isEqualTo(AnalysisError.NOT_RUNNING));
    }

    @Test
    void rerunStartsANewRunWithTheSameSpec() {
        Analysis first = service.start(Fakes.spec("NVDA"));
        runner.sink(first.id()).onEvent(Fakes.finished(1, RunOutcome.COMPLETED, null));
        runner.sink(first.id()).onExit(0);

        Analysis again = service.rerun(first.id());

        assertThat(again.id()).isNotEqualTo(first.id());
        assertThat(again.spec()).isEqualTo(first.spec());
    }

    @Test
    void failsRunsLeftRunningWhoseRunnerIsGone() {
        Analysis orphan = orphan("NVDA", "4242");

        service.afterSingletonsInstantiated();

        Analysis failed = service.get(orphan.id());
        assertThat(failed.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo(AnalysisService.PLATFORM_RESTARTED);
        assertThat(eventStore.read(orphan.id(), 0)).last()
                .satisfies(e -> assertThat(e.type()).isEqualTo(RunEventType.RUN_FINISHED));
    }

    @Test
    void takesTheOutcomeOfRunsThatEndedWhileThePlatformWasDown() {
        Analysis orphan = orphan("NVDA", "4242");
        eventStore.append(orphan.id(), Fakes.stats(1, 12));
        eventStore.append(orphan.id(), Fakes.decision(2, Rating.OVERWEIGHT));
        eventStore.append(orphan.id(), Fakes.finished(3, RunOutcome.COMPLETED, null));

        service.afterSingletonsInstantiated();

        Analysis done = service.get(orphan.id());
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
        assertThat(service.get(orphan.id()).status()).isEqualTo(AnalysisStatus.RUNNING);
        assertThat(service.get(orphan.id()).stats().llmCalls()).isEqualTo(3);
        assertThatThrownBy(() -> service.start(Fakes.spec("NVDA")))
                .isInstanceOfSatisfying(AnalysisException.class,
                        e -> assertThat(e.error()).isEqualTo(AnalysisError.ALREADY_RUNNING));

        service.stop(orphan.id());
        assertThat(runner.stopped).extracting(RunHandle::ref).containsExactly("4242");

        runner.sink(orphan.id()).onEvent(Fakes.finished(2, RunOutcome.STOPPED, null));
        runner.sink(orphan.id()).onExit(-1);
        assertThat(service.get(orphan.id()).status()).isEqualTo(AnalysisStatus.STOPPED);
    }

    @Test
    void queuesRunsLeftQueuedAgainInTheirOrder() {
        Instant now = Instant.now();
        Analysis first = Analysis.queued(AnalysisId.newId(), Fakes.spec("NVDA"), now.minusSeconds(3));
        Analysis second = Analysis.queued(AnalysisId.newId(), Fakes.spec("MU"), now.minusSeconds(2));
        Analysis third = Analysis.queued(AnalysisId.newId(), Fakes.spec("GOOG"), now.minusSeconds(1));
        repository.insert(third);
        repository.insert(first);
        repository.insert(second);

        service.afterSingletonsInstantiated();

        assertThat(runner.sinks.keySet()).containsExactly(first.id(), second.id());
        assertThat(service.get(third.id()).status()).isEqualTo(AnalysisStatus.QUEUED);
    }

    private Analysis orphan(String ticker, String runnerRef) {
        Analysis orphan = Analysis.queued(AnalysisId.newId(), Fakes.spec(ticker), Instant.now())
                .running(Instant.now(), runnerRef);
        repository.insert(orphan);
        return orphan;
    }

    @Test
    void listsNewestFirstWithFilters() {
        service.start(Fakes.spec("NVDA"));
        service.start(Fakes.spec("MU"));

        assertThat(service.list(AnalysisFilter.ALL)).hasSize(2);
        assertThat(service.list(new AnalysisFilter(null, "MU"))).singleElement()
                .satisfies(a -> assertThat(a.spec().ticker()).isEqualTo("MU"));
    }

    @Test
    void unknownIdsAreNotFound() {
        assertThatThrownBy(() -> service.get(new AnalysisId("r_missing")))
                .isInstanceOfSatisfying(AnalysisException.class,
                        e -> assertThat(e.error()).isEqualTo(AnalysisError.NOT_FOUND));
    }

    private void assertInvalid(AnalysisSpec spec, String field) {
        assertThatThrownBy(() -> service.start(spec))
                .isInstanceOfSatisfying(AnalysisException.class, e -> {
                    assertThat(e.error()).isEqualTo(AnalysisError.INVALID_SPEC);
                    assertThat(e.params()).containsEntry("field", field);
                });
    }
}
