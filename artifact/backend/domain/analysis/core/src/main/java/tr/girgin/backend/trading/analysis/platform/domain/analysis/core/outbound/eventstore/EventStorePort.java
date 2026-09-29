package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.eventstore;

import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;

/** A run's event history (the runner's events.jsonl), used to replay events to late subscribers. */
public interface EventStorePort {

    /** Stored events with {@code seq > afterSeq}, in order; empty if the run has none. */
    List<RunEvent> read(AnalysisId id, long afterSeq);

    /** Appends an event the platform produced itself (e.g. the end of a run that died). */
    void append(AnalysisId id, RunEvent event);
}
