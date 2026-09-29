package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service.Fakes.event;
import static tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service.Fakes.finished;
import static tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service.Fakes.spec;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.EventSubscription;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEventType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunOutcome;

class AnalysisEventHubTest {

    private Fakes.Repository repository;
    private Fakes.EventStore eventStore;
    private AnalysisEventHub hub;
    private AnalysisId id;

    @BeforeEach
    void setUp() {
        repository = new Fakes.Repository();
        eventStore = new Fakes.EventStore();
        hub = new AnalysisEventHub(repository, eventStore);
        Analysis running = Analysis.queued(AnalysisId.newId(), spec("NVDA"), Instant.now()).running(Instant.now());
        repository.insert(running);
        id = running.id();
    }

    @Test
    void replaysHistoryAfterTheCursorThenDeliversLiveEvents() {
        store(event(1, RunEventType.RUN_STARTED), event(2, RunEventType.AGENT_STATUS), event(3, RunEventType.MESSAGE));
        Fakes.RecordingListener listener = new Fakes.RecordingListener();

        hub.subscribe(id, 1, listener);
        hub.publish(id, event(4, RunEventType.MESSAGE));

        assertThat(listener.seqs).containsExactly(2L, 3L, 4L);
        assertThat(listener.completed).isFalse();
    }

    @Test
    void dropsLiveEventsAlreadySeenInTheReplay() {
        store(event(1, RunEventType.RUN_STARTED), event(2, RunEventType.MESSAGE));
        Fakes.RecordingListener listener = new Fakes.RecordingListener();
        hub.subscribe(id, 0, listener);

        // The runner writes the file before stdout, so a live event may repeat a replayed one.
        hub.publish(id, event(2, RunEventType.MESSAGE));
        hub.publish(id, event(3, RunEventType.MESSAGE));

        assertThat(listener.seqs).containsExactly(1L, 2L, 3L);
    }

    @Test
    void completesSubscribersWhenTheRunFinishes() {
        Fakes.RecordingListener listener = new Fakes.RecordingListener();
        hub.subscribe(id, 0, listener);

        hub.publish(id, finished(1, RunOutcome.COMPLETED, null));
        hub.publish(id, event(2, RunEventType.LOG));

        assertThat(listener.seqs).containsExactly(1L);
        assertThat(listener.completed).isTrue();
    }

    @Test
    void replaysAFinishedRunAndCompletesAtOnce() {
        store(event(1, RunEventType.RUN_STARTED), finished(2, RunOutcome.COMPLETED, null));
        repository.update(repository.findById(id).orElseThrow()
                .finished(AnalysisStatus.COMPLETED, Instant.now(), null, null));
        Fakes.RecordingListener listener = new Fakes.RecordingListener();

        hub.subscribe(id, 0, listener);

        assertThat(listener.seqs).containsExactly(1L, 2L);
        assertThat(listener.completed).isTrue();
    }

    @Test
    void cancelledSubscribersGetNothingMore() {
        Fakes.RecordingListener listener = new Fakes.RecordingListener();
        EventSubscription subscription = hub.subscribe(id, 0, listener);

        subscription.cancel();
        hub.publish(id, event(1, RunEventType.MESSAGE));

        assertThat(listener.seqs).isEmpty();
    }

    @Test
    void aFailingListenerDoesNotAffectOthers() {
        Fakes.RecordingListener healthy = new Fakes.RecordingListener();
        hub.subscribe(id, 0, new Fakes.RecordingListener() {
            @Override
            public void onEvent(RunEvent event) {
                throw new IllegalStateException("client went away");
            }
        });
        hub.subscribe(id, 0, healthy);

        hub.publish(id, event(1, RunEventType.MESSAGE));
        hub.publish(id, event(2, RunEventType.MESSAGE));

        assertThat(healthy.seqs).containsExactly(1L, 2L);
    }

    private void store(RunEvent... events) {
        for (RunEvent event : events) {
            eventStore.append(id, event);
        }
    }
}
