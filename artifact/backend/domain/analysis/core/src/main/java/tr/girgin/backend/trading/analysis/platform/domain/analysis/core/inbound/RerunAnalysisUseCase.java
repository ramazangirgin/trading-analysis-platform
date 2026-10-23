package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

/** Runs an analysis again. */
public interface RerunAnalysisUseCase {

    /**
     * Starts a new run with the same spec, owned by the caller. The new run is private. An analysis the
     * caller may not manage, a public one of another user included, is reported as not found.
     */
    Analysis rerun(AnalysisId id, AnalysisAccess access);
}
