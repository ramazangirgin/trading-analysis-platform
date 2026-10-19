package tr.girgin.backend.trading.analysis.platform.orchestration.report.inbound;

import java.util.Optional;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.PriceHistory;

public interface GetAnalysisPricesUseCase {

    /**
     * About a year of daily prices up to the analysis' trade date, from upstream's price cache. An analysis the
     * caller may not see is reported as not found.
     */
    Optional<PriceHistory> getPrices(AnalysisId id, AnalysisAccess access);
}
