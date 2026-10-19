package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;

/** Starts an analysis run. */
public interface StartAnalysisUseCase {

    /** Queues the run for {@code owner}; it starts as soon as a runner slot is free. */
    Analysis start(AnalysisSpec spec, OwnerId owner);
}
