package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

public interface StopAnalysisUseCase {

    /** A queued run stops at once; a running one when the runner has shut down. */
    Analysis stop(AnalysisId id);
}
