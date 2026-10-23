package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisScope;

/** Lists analysis runs. */
public interface ListAnalysesUseCase {

    /**
     * Newest first. {@code MINE} is the caller's own runs, whatever the filter's owner; {@code SHARED} the public
     * runs of other users; {@code ALL} the filter as given, but only with {@code allUsers}, otherwise it is
     * {@code MINE}.
     */
    List<Analysis> list(AnalysisFilter filter, AnalysisScope scope, AnalysisAccess access);
}
