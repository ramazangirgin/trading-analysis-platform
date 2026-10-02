package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;

/** Receives a run's events, in order, from {@link SubscribeAnalysisEventsUseCase}. */
public interface RunEventListener {

    void onEvent(RunEvent event);

    /** The run has finished; no more events follow. */
    void onComplete();
}
