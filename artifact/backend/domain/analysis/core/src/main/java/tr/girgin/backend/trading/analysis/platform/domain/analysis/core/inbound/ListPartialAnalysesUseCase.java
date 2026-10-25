package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.PartialAnalysisPage;

/** Lists the partial analyses for a caller without a session. */
public interface ListPartialAnalysesUseCase {

    /** Newest first; {@code page} counts from 0. */
    PartialAnalysisPage list(int page, int size);
}
