package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;

/** Tells what a caller may do with an analysis. */
public interface GetAnalysisRightsUseCase {

    /** Returns whether the caller manages the analysis and to which visibilities they may change it. */
    AnalysisRights rights(Analysis analysis, AnalysisAccess access);
}
