package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;

/** Starts an analysis run. */
public interface StartAnalysisUseCase {

    /** Queues the run; it starts as soon as a runner slot is free. */
    Analysis start(AnalysisSpec spec);
}
