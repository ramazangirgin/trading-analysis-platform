package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

/** Reads one analysis run. */
public interface GetAnalysisUseCase {

    /** An analysis the caller may not see is reported as not found, like a missing one. */
    Analysis get(AnalysisId id, AnalysisAccess access);
}
