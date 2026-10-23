package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

/** Stops a queued or running analysis. */
public interface StopAnalysisUseCase {

    /**
     * A queued run stops at once; a running one when the runner has shut down. An analysis the caller may not
     * manage, a public one of another user included, is reported as not found.
     */
    Analysis stop(AnalysisId id, AnalysisAccess access);
}
