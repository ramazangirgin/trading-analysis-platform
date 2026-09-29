package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner;

import java.util.Map;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;

/** Runs ta-runner somewhere (local process, Docker container) and streams its events back. */
public interface RunnerPort {

    /**
     * Starts the run and returns immediately. Events and the exit arrive on {@code sink},
     * from a runner-owned thread.
     */
    RunHandle start(AnalysisId id, AnalysisSpec spec, Map<String, String> environment, RunEventSink sink);

    /** Asks the run to stop (SIGTERM, then a forced kill after a grace period). */
    void stop(RunHandle handle);
}
