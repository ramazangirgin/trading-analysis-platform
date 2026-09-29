package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

public interface SubscribeAnalysisEventsUseCase {

    /**
     * Replays the stored events with {@code seq > afterSeq}, then delivers live ones in order and
     * without duplicates. The listener completes once the run has finished.
     */
    EventSubscription subscribe(AnalysisId id, long afterSeq, RunEventListener listener);
}
