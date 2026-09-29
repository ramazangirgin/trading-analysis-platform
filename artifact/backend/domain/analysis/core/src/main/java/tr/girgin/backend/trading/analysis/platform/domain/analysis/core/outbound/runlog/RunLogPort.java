package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runlog;

import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

public interface RunLogPort {

    /** The last lines of the run's log; empty if it has none. */
    List<String> tail(AnalysisId id, int maxLines);
}
