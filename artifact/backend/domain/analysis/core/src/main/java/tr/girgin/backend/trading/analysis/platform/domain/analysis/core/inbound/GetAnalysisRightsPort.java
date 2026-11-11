package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisRights;

/** Tells what a caller may do with an analysis. */
public interface GetAnalysisRightsPort {

    AnalysisRights rights(Analysis analysis, AnalysisAccess access);
}
