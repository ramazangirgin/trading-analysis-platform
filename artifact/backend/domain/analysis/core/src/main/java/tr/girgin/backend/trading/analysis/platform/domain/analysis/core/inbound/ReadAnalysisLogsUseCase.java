package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

/** Reads a run's log. */
public interface ReadAnalysisLogsUseCase {

    /**
     * The last {@code maxLines} lines of the runner's stderr log, secrets masked. An analysis the caller may
     * not see is reported as not found.
     */
    List<String> tailLogs(AnalysisId id, AnalysisAccess access, int maxLines);
}
