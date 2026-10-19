package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;

/** Lists analysis runs. */
public interface ListAnalysesUseCase {

    /** Newest first. Without {@code allUsers}, only the caller's own runs, whatever the filter's owner. */
    List<Analysis> list(AnalysisFilter filter, AnalysisAccess access);
}
