package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.EventSubscription;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.RunEventListener;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.SubscribeAnalysisEventsUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEventType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.eventstore.EventStorePort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.persistence.AnalysisRepositoryPort;

/**
 * Fans run events out to subscribers. A subscriber first gets the stored history after its
 * cursor, then live events; events arriving during the replay are buffered and de-duplicated by
 * {@code seq}, so nothing is lost or repeated across a reconnect.
 */
@Service
class AnalysisEventHub implements SubscribeAnalysisEventsUseCase {

    private static final Logger log = LoggerFactory.getLogger(AnalysisEventHub.class);

    private final AnalysisRepositoryPort repository;
    private final EventStorePort eventStore;
    private final Map<AnalysisId, List<Subscriber>> subscribers = new ConcurrentHashMap<>();

    AnalysisEventHub(AnalysisRepositoryPort repository, EventStorePort eventStore) {
        this.repository = repository;
        this.eventStore = eventStore;
    }

    @Override
    public EventSubscription subscribe(AnalysisId id, long afterSeq, RunEventListener listener) {
        repository
                .findById(id)
                .orElseThrow(() -> new AnalysisException(
                        AnalysisError.NOT_FOUND, "Analysis not found: " + id, Map.of("id", id.value())));
        Subscriber subscriber = new Subscriber(listener, afterSeq);
        subscribers.computeIfAbsent(id, _ -> new CopyOnWriteArrayList<>()).add(subscriber);
        subscriber.replay(eventStore.read(id, afterSeq));
        // A run that ended before (or while) we registered sends no more live events.
        if (repository
                .findById(id)
                .map(Analysis::status)
                .map(s -> s.isTerminal())
                .orElse(true)) {
            subscriber.complete();
            remove(id, subscriber);
        }
        return () -> remove(id, subscriber);
    }

    void publish(AnalysisId id, RunEvent event) {
        List<Subscriber> current = subscribers.getOrDefault(id, List.of());
        for (Subscriber subscriber : current) {
            subscriber.deliver(event);
        }
        if (event.type() == RunEventType.RUN_FINISHED) {
            List<Subscriber> finished = subscribers.remove(id);
            if (finished != null) {
                finished.forEach(Subscriber::complete);
            }
        }
    }

    private void remove(AnalysisId id, Subscriber subscriber) {
        subscribers.computeIfPresent(id, (_, list) -> {
            list.remove(subscriber);
            return list.isEmpty() ? null : list;
        });
    }

    private static final class Subscriber {

        private final RunEventListener listener;
        private final List<RunEvent> buffered = new ArrayList<>();
        private long lastSeq;
        private boolean replaying = true;
        private boolean done;

        Subscriber(RunEventListener listener, long afterSeq) {
            this.listener = listener;
            this.lastSeq = afterSeq;
        }

        synchronized void replay(List<RunEvent> history) {
            history.forEach(this::emit);
            replaying = false;
            buffered.forEach(this::emit);
            buffered.clear();
        }

        synchronized void deliver(RunEvent event) {
            if (replaying) {
                buffered.add(event);
            } else {
                emit(event);
            }
        }

        synchronized void complete() {
            if (!done) {
                done = true;
                listener.onComplete();
            }
        }

        @SuppressWarnings("checkstyle:IllegalCatch") // a failing subscriber must not affect the run
        private void emit(RunEvent event) {
            if (done || event.seq() <= lastSeq) {
                return;
            }
            lastSeq = event.seq();
            try {
                listener.onEvent(event);
            } catch (RuntimeException e) {
                // A broken client connection must not affect the run or other subscribers.
                log.debug("Dropping subscriber after delivery failure", e);
                done = true;
            }
        }
    }
}
