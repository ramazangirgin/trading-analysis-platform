package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

public interface ReadAnalysisLogsUseCase {

    /** The last {@code maxLines} lines of the runner's stderr log, secrets masked. */
    List<String> tailLogs(AnalysisId id, int maxLines);
}
