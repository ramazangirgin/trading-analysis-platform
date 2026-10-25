package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

/** Reads one partial analysis for a caller without a session. */
public interface GetPartialAnalysisUseCase {

    /**
     * Returns the analysis only when it is partial and completed.
     *
     * @throws tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException with
     *     NOT_FOUND for anything else, so a private or public analysis is not revealed
     */
    Analysis get(AnalysisId id);
}
