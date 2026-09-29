package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;

/** Receives what a started run produces. Implemented by the analysis service. */
public interface RunEventSink {

    void onEvent(RunEvent event);

    /** The runner process or container has exited; called exactly once, after the last event. */
    void onExit(int exitCode);
}
