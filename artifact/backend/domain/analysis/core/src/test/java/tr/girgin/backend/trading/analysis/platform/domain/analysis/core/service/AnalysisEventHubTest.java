package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.EventSubscription;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEventType;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunOutcome;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Visibility;

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
        Analysis running = Analysis.queued(AnalysisId.newId(), Fakes.spec("NVDA"), Fakes.OWNER, Instant.now())
                .running(Instant.now(), "4242");
        repository.insert(running);
        id = running.id();
    }

    @Test
    void replaysHistoryAfterTheCursorThenDeliversLiveEvents() {
        store(
                Fakes.event(1, RunEventType.RUN_STARTED),
                Fakes.event(2, RunEventType.AGENT_STATUS),
                Fakes.event(3, RunEventType.MESSAGE));
        Fakes.RecordingListener listener = new Fakes.RecordingListener();

        hub.subscribe(id, 1, Fakes.OWN, listener);
        hub.publish(id, Fakes.event(4, RunEventType.MESSAGE));

        assertThat(listener.seqs).containsExactly(2L, 3L, 4L);
        assertThat(listener.completed).isFalse();
    }

    @Test
    void dropsLiveEventsAlreadySeenInTheReplay() {
        store(Fakes.event(1, RunEventType.RUN_STARTED), Fakes.event(2, RunEventType.MESSAGE));
        Fakes.RecordingListener listener = new Fakes.RecordingListener();
        hub.subscribe(id, 0, Fakes.OWN, listener);

        // The runner writes the file before stdout, so a live event may repeat a replayed one.
        hub.publish(id, Fakes.event(2, RunEventType.MESSAGE));
        hub.publish(id, Fakes.event(3, RunEventType.MESSAGE));

        assertThat(listener.seqs).containsExactly(1L, 2L, 3L);
    }

    @Test
    void completesSubscribersWhenTheRunFinishes() {
        Fakes.RecordingListener listener = new Fakes.RecordingListener();
        hub.subscribe(id, 0, Fakes.OWN, listener);

        hub.publish(id, Fakes.finished(1, RunOutcome.COMPLETED, null));
        hub.publish(id, Fakes.event(2, RunEventType.LOG));

        assertThat(listener.seqs).containsExactly(1L);
        assertThat(listener.completed).isTrue();
    }

    @Test
    void replaysAFinishedRunAndCompletesAtOnce() {
        store(Fakes.event(1, RunEventType.RUN_STARTED), Fakes.finished(2, RunOutcome.COMPLETED, null));
        repository.update(
                repository.findById(id).orElseThrow().finished(AnalysisStatus.COMPLETED, Instant.now(), null, null));
        Fakes.RecordingListener listener = new Fakes.RecordingListener();

        hub.subscribe(id, 0, Fakes.OWN, listener);

        assertThat(listener.seqs).containsExactly(1L, 2L);
        assertThat(listener.completed).isTrue();
    }

    @Test
    void refusesAnotherUsersRunBeforeDeliveringAnyEvent() {
        store(Fakes.event(1, RunEventType.RUN_STARTED), Fakes.event(2, RunEventType.MESSAGE));
        Fakes.RecordingListener listener = new Fakes.RecordingListener();

        assertThatThrownBy(() -> hub.subscribe(id, 0, Fakes.OTHERS, listener))
                .isInstanceOfSatisfying(
                        AnalysisException.class, e -> assertThat(e.error()).isEqualTo(AnalysisError.NOT_FOUND));
        hub.publish(id, Fakes.event(3, RunEventType.MESSAGE));

        assertThat(listener.seqs).isEmpty();
        assertThat(listener.completed).isFalse();
    }

    @Test
    void replaysAnotherUsersRunWithAllUsers() {
        store(Fakes.event(1, RunEventType.RUN_STARTED), Fakes.event(2, RunEventType.MESSAGE));
        Fakes.RecordingListener listener = new Fakes.RecordingListener();

        hub.subscribe(id, 0, Fakes.EVERYONE, listener);

        assertThat(listener.seqs).containsExactly(1L, 2L);
    }

    @Test
    void replaysAnotherUsersPublicRunButNotAPrivateOne() {
        store(Fakes.event(1, RunEventType.RUN_STARTED), Fakes.finished(2, RunOutcome.COMPLETED, null));
        Analysis completed =
                repository.findById(id).orElseThrow().finished(AnalysisStatus.COMPLETED, Instant.now(), null, null);
        repository.update(completed);
        Fakes.RecordingListener refused = new Fakes.RecordingListener();
        assertThatThrownBy(() -> hub.subscribe(id, 0, Fakes.OTHERS, refused))
                .isInstanceOfSatisfying(
                        AnalysisException.class, e -> assertThat(e.error()).isEqualTo(AnalysisError.NOT_FOUND));

        repository.update(completed.withVisibility(Visibility.PUBLIC, Instant.now()));
        Fakes.RecordingListener listener = new Fakes.RecordingListener();
        hub.subscribe(id, 0, Fakes.OTHERS, listener);

        assertThat(listener.seqs).containsExactly(1L, 2L);
        assertThat(listener.completed).isTrue();
    }

    @Test
    void cancelledSubscribersGetNothingMore() {
        Fakes.RecordingListener listener = new Fakes.RecordingListener();
        EventSubscription subscription = hub.subscribe(id, 0, Fakes.OWN, listener);

        subscription.cancel();
        hub.publish(id, Fakes.event(1, RunEventType.MESSAGE));

        assertThat(listener.seqs).isEmpty();
    }

    @Test
    void aFailingListenerDoesNotAffectOthers() {
        Fakes.RecordingListener healthy = new Fakes.RecordingListener();
        hub.subscribe(id, 0, Fakes.OWN, new Fakes.RecordingListener() {
            @Override
            public void onEvent(RunEvent event) {
                throw new IllegalStateException("client went away");
            }
        });
        hub.subscribe(id, 0, Fakes.OWN, healthy);

        hub.publish(id, Fakes.event(1, RunEventType.MESSAGE));
        hub.publish(id, Fakes.event(2, RunEventType.MESSAGE));

        assertThat(healthy.seqs).containsExactly(1L, 2L);
    }

    private void store(RunEvent... events) {
        for (RunEvent event : events) {
            eventStore.append(id, event);
        }
    }
}
