package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.GetAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.ListAnalysesUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.RerunAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.StartAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.StopAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEventType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunOutcome;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.credentials.CredentialsPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.eventstore.EventStorePort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence.AnalysisRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunEventSink;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunHandle;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner.RunnerPort;

/**
 * Run lifecycle: a FIFO queue in front of a fixed number of runner slots, at most one active run
 * per ticker and trade date (they would write the same report and cache files), and the status
 * transitions driven by runner events.
 */
@Service
class AnalysisService
        implements StartAnalysisUseCase,
                StopAnalysisUseCase,
                RerunAnalysisUseCase,
                GetAnalysisUseCase,
                ListAnalysesUseCase,
                SmartInitializingSingleton {

    // Error codes stored on failed runs; the frontend translates them.
    static final String RUNNER_ERROR = "runner_error";
    static final String RUNNER_DIED = "runner_died";
    static final String RUNNER_START_FAILED = "runner_start_failed";
    static final String PLATFORM_RESTARTED = "platform_restarted";

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final AnalysisRepositoryPort repository;
    private final RunnerPort runner;
    private final CredentialsPort credentials;
    private final EventStorePort eventStore;
    private final AnalysisEventHub hub;
    private final Clock clock;
    private final int maxConcurrentRuns;

    private final Object lock = new Object();
    // Guarded by lock. "active" = queued or running, keyed for the ticker/date check.
    private final Deque<AnalysisId> queue = new ArrayDeque<>();
    private final Map<AnalysisId, RunHandle> running = new HashMap<>();
    private final Map<AnalysisId, AnalysisSpec> active = new LinkedHashMap<>();

    AnalysisService(
            AnalysisRepositoryPort repository,
            RunnerPort runner,
            CredentialsPort credentials,
            EventStorePort eventStore,
            AnalysisEventHub hub,
            Clock clock,
            @Value("${platform.analysis.max-concurrent-runs:2}") int maxConcurrentRuns) {
        if (maxConcurrentRuns < 1) {
            throw new IllegalArgumentException("platform.analysis.max-concurrent-runs must be at least 1");
        }
        this.repository = repository;
        this.runner = runner;
        this.credentials = credentials;
        this.eventStore = eventStore;
        this.hub = hub;
        this.clock = clock;
        this.maxConcurrentRuns = maxConcurrentRuns;
    }

    /**
     * Picks up what a previous platform process left active: a run whose runner is still going is
     * followed again, one that ended meanwhile gets its real outcome from its events, and queued
     * runs go back in the queue, in the order they were asked for.
     */
    @Override
    public void afterSingletonsInstantiated() {
        List<Analysis> orphans =
                repository.findByStatusIn(Set.of(AnalysisStatus.QUEUED, AnalysisStatus.RUNNING)).stream()
                        .sorted(Comparator.comparing(Analysis::createdAt))
                        .toList();
        synchronized (lock) {
            orphans.stream().filter(a -> a.status() == AnalysisStatus.RUNNING).forEach(this::reconcile);
            orphans.stream().filter(a -> a.status() == AnalysisStatus.QUEUED).forEach(queued -> {
                log.info("Queueing {} again after a platform restart", queued.id());
                queue.addLast(queued.id());
                active.put(queued.id(), queued.spec());
            });
            dispatch();
        }
    }

    /** A run that was RUNNING when the platform stopped. Caller holds the lock. */
    private void reconcile(Analysis orphan) {
        AnalysisId id = orphan.id();
        Sink sink = new Sink(id);
        // Catch up on what the runner wrote while no platform was reading it.
        List<RunEvent> missed = eventStore.read(id, 0);
        missed.forEach(sink::onEvent);
        if (sink.finishedReported) {
            log.info("{} ended while the platform was down: {}", id, get(id).status());
            return;
        }
        long lastSeq = missed.stream().mapToLong(RunEvent::seq).max().orElse(0);
        RunHandle handle = orphan.runnerRef() == null ? null : new RunHandle(orphan.runnerRef());
        if (handle != null && runner.reattach(id, handle, lastSeq, sink)) {
            running.put(id, handle);
            active.put(id, orphan.spec());
            log.info("Following {} again ({}) after a platform restart", id, handle.ref());
            return;
        }
        log.warn("Marking {} as failed: its runner is gone after a platform restart", id);
        end(
                get(id),
                RunOutcome.FAILED,
                PLATFORM_RESTARTED,
                "The platform restarted while this run was running, and the runner did not survive");
    }

    @Override
    public Analysis start(AnalysisSpec spec) {
        AnalysisSpecValidator.validate(spec, LocalDate.now(clock));
        Analysis analysis = Analysis.queued(AnalysisId.newId(), spec, clock.instant());
        synchronized (lock) {
            ensureNotActive(spec);
            repository.insert(analysis);
            queue.addLast(analysis.id());
            active.put(analysis.id(), spec);
            log.info("Queued {} for {} on {}", analysis.id(), spec.ticker(), spec.tradeDate());
            dispatch();
        }
        return get(analysis.id());
    }

    @Override
    public Analysis rerun(AnalysisId id) {
        return start(get(id).spec());
    }

    @Override
    public Analysis stop(AnalysisId id) {
        Analysis analysis = get(id);
        RunHandle handle;
        synchronized (lock) {
            if (queue.remove(id)) {
                active.remove(id);
                end(analysis, RunOutcome.STOPPED, null, null);
                return get(id);
            }
            handle = running.get(id);
        }
        if (handle == null) {
            throw new AnalysisException(
                    AnalysisError.NOT_RUNNING,
                    "Analysis is not running: " + id,
                    Map.of("id", id.value(), "status", analysis.status().name()));
        }
        log.info("Stopping {}", id);
        runner.stop(handle);
        return get(id);
    }

    @Override
    public Analysis get(AnalysisId id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new AnalysisException(
                        AnalysisError.NOT_FOUND, "Analysis not found: " + id, Map.of("id", id.value())));
    }

    @Override
    public List<Analysis> list(AnalysisFilter filter) {
        return repository.findAll(filter);
    }

    private void ensureNotActive(AnalysisSpec spec) {
        active.entrySet().stream()
                .filter(e -> e.getValue().ticker().equals(spec.ticker())
                        && e.getValue().tradeDate().equals(spec.tradeDate()))
                .findFirst()
                .ifPresent(e -> {
                    throw new AnalysisException(
                            AnalysisError.ALREADY_RUNNING,
                            "An analysis for " + spec.ticker() + " on " + spec.tradeDate() + " is already active",
                            Map.of(
                                    "id",
                                    e.getKey().value(),
                                    "ticker",
                                    spec.ticker(),
                                    "tradeDate",
                                    spec.tradeDate().toString()));
                });
    }

    /**
     * A record someone else wrote meanwhile is not overwritten.
     *
     * @throws AnalysisException with CONCURRENT_UPDATE when the record's version is not the stored one
     */
    private void update(Analysis analysis) {
        try {
            repository.update(analysis);
        } catch (OptimisticLockingFailureException _) {
            throw new AnalysisException(
                    AnalysisError.CONCURRENT_UPDATE,
                    "Analysis was changed concurrently: " + analysis.id(),
                    Map.of("id", analysis.id().value()));
        }
    }

    /** Starts queued runs while slots are free. Caller holds the lock. */
    @SuppressWarnings("checkstyle:IllegalCatch") // any start failure fails the run, not the queue
    private void dispatch() {
        while (running.size() < maxConcurrentRuns && !queue.isEmpty()) {
            AnalysisId id = queue.pollFirst();
            Analysis analysis = get(id);
            try {
                RunHandle handle = runner.start(id, analysis.spec(), credentials.environment(), new Sink(id));
                running.put(id, handle);
                update(analysis.running(clock.instant(), handle.ref()));
                log.info("Started {} ({})", id, handle.ref());
            } catch (RuntimeException e) {
                log.error("Could not start {}", id, e);
                active.remove(id);
                end(analysis, RunOutcome.FAILED, RUNNER_START_FAILED, e.getMessage());
            }
        }
    }

    /**
     * Ends a run the runner did not report on (stopped while queued, failed to start, died): the
     * end is recorded and a RUN_FINISHED event appended, so replaying clients see it too.
     */
    private void end(Analysis analysis, RunOutcome outcome, String errorCode, String message) {
        update(analysis.finished(outcome.toStatus(), clock.instant(), errorCode, message));
        long seq = eventStore.read(analysis.id(), 0).stream()
                        .mapToLong(RunEvent::seq)
                        .max()
                        .orElse(0)
                + 1;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(
                "status",
                switch (outcome) {
                    case COMPLETED -> "completed";
                    case STOPPED -> "stopped";
                    case FAILED -> "error";
                });
        if (errorCode != null) {
            payload.put("error_code", errorCode);
            payload.put("error", message);
        }
        RunEvent event =
                new RunEvent(seq, clock.instant(), RunEventType.RUN_FINISHED, null, outcome, null, message, payload);
        eventStore.append(analysis.id(), event);
        hub.publish(analysis.id(), event);
    }

    /** Applies one run's events to its record, then fans them out. Called from the runner's thread. */
    private final class Sink implements RunEventSink {

        private final AnalysisId id;
        private volatile boolean finishedReported;

        Sink(AnalysisId id) {
            this.id = id;
        }

        @Override
        public void onEvent(RunEvent event) {
            synchronized (lock) {
                Analysis analysis = get(id);
                Analysis updated = switch (event.type()) {
                    case STATS -> event.stats() == null ? analysis : analysis.withStats(event.stats());
                    case DECISION ->
                        analysis.withDecision(
                                event.rating(), (String) event.payload().get("raw"));
                    case RUN_FINISHED -> {
                        finishedReported = true;
                        RunOutcome outcome = event.outcome() == null ? RunOutcome.FAILED : event.outcome();
                        if (outcome != RunOutcome.FAILED) {
                            yield analysis.finished(outcome.toStatus(), event.timestamp(), null, null);
                        }
                        Object type = event.payload().get("error_type");
                        String message = type == null ? event.error() : type + ": " + event.error();
                        yield analysis.finished(AnalysisStatus.FAILED, event.timestamp(), RUNNER_ERROR, message);
                    }
                    default -> analysis;
                };
                if (updated != analysis) {
                    update(updated);
                }
            }
            hub.publish(id, event);
        }

        @Override
        public void onExit(int exitCode) {
            synchronized (lock) {
                running.remove(id);
                active.remove(id);
                if (!finishedReported) {
                    log.warn("{} exited with code {} without reporting an end", id, exitCode);
                    end(get(id), RunOutcome.FAILED, RUNNER_DIED, "Runner exited with code " + exitCode);
                } else {
                    log.info("{} exited with code {}", id, exitCode);
                }
                dispatch();
            }
        }
    }
}
