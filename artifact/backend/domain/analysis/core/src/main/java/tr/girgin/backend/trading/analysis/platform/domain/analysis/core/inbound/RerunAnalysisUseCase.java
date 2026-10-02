package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

/** Runs an analysis again. */
public interface RerunAnalysisUseCase {

    /** Starts a new run with the same spec. */
    Analysis rerun(AnalysisId id);
}
