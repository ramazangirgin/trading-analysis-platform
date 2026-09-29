package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service.Fakes.decision;
import static tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service.Fakes.event;
import static tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service.Fakes.finished;
import static tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service.Fakes.spec;
import static tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service.Fakes.stats;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analyst;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AssetType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Rating;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEventType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunOutcome;

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
        Analysis analysis = service.start(spec("nvda"));

        assertThat(analysis.status()).isEqualTo(AnalysisStatus.RUNNING);
        assertThat(analysis.spec().ticker()).isEqualTo("NVDA");
        assertThat(analysis.startedAt()).isNotNull();
        assertThat(runner.sinks).containsKey(analysis.id());
        assertThat(runner.lastEnvironment).containsEntry("DEEPSEEK_API_KEY", "sk-test");
    }

    @Test
    void queuesBeyondTheConcurrencyLimitAndStartsWhenASlotFrees() {
        Analysis first = service.start(spec("NVDA"));
        service.start(spec("MU"));
        Analysis third = service.start(spec("GOOG"));

        assertThat(third.status()).isEqualTo(AnalysisStatus.QUEUED);
        assertThat(runner.sinks).hasSize(2);

        runner.sink(first.id()).onEvent(finished(1, RunOutcome.COMPLETED, null));
        runner.sink(first.id()).onExit(0);

        assertThat(service.get(third.id()).status()).isEqualTo(AnalysisStatus.RUNNING);
        assertThat(runner.sinks).containsKey(third.id());
    }

    @Test
    void rejectsASecondActiveRunForTheSameTickerAndDate() {
        Analysis first = service.start(spec("NVDA"));

        assertThatThrownBy(() -> service.start(spec("nvda")))
                .isInstanceOfSatisfying(AnalysisException.class, e -> {
                    assertThat(e.error()).isEqualTo(AnalysisError.ALREADY_RUNNING);
                    assertThat(e.params()).containsEntry("id", first.id().value());
                });
    }

    @Test
    void allowsTheSameTickerAgainOnceTheFirstRunEnded() {
        Analysis first = service.start(spec("NVDA"));
        runner.sink(first.id()).onEvent(finished(1, RunOutcome.COMPLETED, null));
        runner.sink(first.id()).onExit(0);

        assertThat(service.start(spec("NVDA")).status()).isEqualTo(AnalysisStatus.RUNNING);
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
        AnalysisId id = service.start(spec("NVDA")).id();
        var sink = runner.sink(id);

        sink.onEvent(event(1, RunEventType.RUN_STARTED));
        sink.onEvent(stats(2, 7));
        sink.onEvent(decision(3, Rating.OVERWEIGHT));
        sink.onEvent(finished(4, RunOutcome.COMPLETED, null));
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
        AnalysisId id = service.start(spec("NVDA")).id();

        runner.sink(id).onEvent(finished(1, RunOutcome.FAILED, "provider exploded"));
        runner.sink(id).onExit(1);

        Analysis failed = service.get(id);
        assertThat(failed.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo(AnalysisService.RUNNER_ERROR);
        assertThat(failed.errorMessage()).isEqualTo("RuntimeError: provider exploded");
    }

    @Test
    void marksARunThatDiedWithoutReportingAsFailedAndAppendsTheEnd() {
        AnalysisId id = service.start(spec("NVDA")).id();
        runner.sink(id).onEvent(event(1, RunEventType.RUN_STARTED));

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

        Analysis analysis = service.start(spec("NVDA"));

        assertThat(analysis.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.errorCode()).isEqualTo(AnalysisService.RUNNER_START_FAILED);
        assertThat(analysis.errorMessage()).isEqualTo("python not found");
    }

    @Test
    void stopsAQueuedRunAtOnce() {
        service.start(spec("NVDA"));
        service.start(spec("MU"));
        AnalysisId queued = service.start(spec("GOOG")).id();

        Analysis stopped = service.stop(queued);

        assertThat(stopped.status()).isEqualTo(AnalysisStatus.STOPPED);
        assertThat(runner.stopped).isEmpty();
    }

    @Test
    void asksTheRunnerToStopARunningRun() {
        AnalysisId id = service.start(spec("NVDA")).id();

        service.stop(id);

        assertThat(runner.stopped).extracting(h -> h.ref()).containsExactly("pid-" + id.value());
    }

    @Test
    void refusesToStopAFinishedRun() {
        AnalysisId id = service.start(spec("NVDA")).id();
        runner.sink(id).onEvent(finished(1, RunOutcome.COMPLETED, null));
        runner.sink(id).onExit(0);

        assertThatThrownBy(() -> service.stop(id))
                .isInstanceOfSatisfying(AnalysisException.class,
                        e -> assertThat(e.error()).isEqualTo(AnalysisError.NOT_RUNNING));
    }

    @Test
    void rerunStartsANewRunWithTheSameSpec() {
        Analysis first = service.start(spec("NVDA"));
        runner.sink(first.id()).onEvent(finished(1, RunOutcome.COMPLETED, null));
        runner.sink(first.id()).onExit(0);

        Analysis again = service.rerun(first.id());

        assertThat(again.id()).isNotEqualTo(first.id());
        assertThat(again.spec()).isEqualTo(first.spec());
    }

    @Test
    void failsRunsLeftActiveByAPreviousProcess() {
        Analysis orphan = Analysis.queued(AnalysisId.newId(), spec("NVDA"), Instant.now()).running(Instant.now());
        repository.insert(orphan);

        service.afterSingletonsInstantiated();

        assertThat(service.get(orphan.id()).status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(service.get(orphan.id()).errorCode()).isEqualTo(AnalysisService.PLATFORM_RESTARTED);
    }

    @Test
    void listsNewestFirstWithFilters() {
        service.start(spec("NVDA"));
        service.start(spec("MU"));

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
