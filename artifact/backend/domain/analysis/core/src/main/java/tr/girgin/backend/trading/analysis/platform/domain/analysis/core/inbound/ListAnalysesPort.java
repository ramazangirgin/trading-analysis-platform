package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisFilter;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisScope;

/** Lists analysis runs. */
public interface ListAnalysesPort {

    List<Analysis> list(AnalysisFilter filter, AnalysisScope scope, AnalysisAccess access);
}
