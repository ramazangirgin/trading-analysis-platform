package tr.girgin.backend.trading.analysis.platform.orchestration.report.inbound;

import java.util.Optional;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.GetAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.GetPriceHistoryUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.PriceHistory;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;

@Service
public class GetAnalysisPricesUseCase {

    private final GetAnalysisUseCase getAnalysis;
    private final GetPriceHistoryUseCase getPrices;

    GetAnalysisPricesUseCase(GetAnalysisUseCase getAnalysis, GetPriceHistoryUseCase getPrices) {
        this.getAnalysis = getAnalysis;
        this.getPrices = getPrices;
    }

    /**
     * About a year of daily prices up to the analysis' trade date, from upstream's price cache. An analysis the
     * caller may not see is reported as not found.
     */
    public Optional<PriceHistory> getPrices(AnalysisId id, AnalysisAccess access) {
        Analysis analysis = getAnalysis.get(id, access);
        return ReportKey.isValidTicker(analysis.spec().ticker())
                ? getPrices.get(
                        new ReportKey(analysis.spec().ticker(), analysis.spec().tradeDate()))
                : Optional.empty();
    }
}
