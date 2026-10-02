package tr.girgin.backend.trading.analysis.platform.orchestration.report.service;

import java.util.Optional;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.GetAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.GetPriceHistoryUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.GetReportUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.PriceHistory;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;
import tr.girgin.backend.trading.analysis.platform.orchestration.report.inbound.GetAnalysisPricesUseCase;
import tr.girgin.backend.trading.analysis.platform.orchestration.report.inbound.GetAnalysisReportUseCase;

@Service
class AnalysisReportService implements GetAnalysisReportUseCase, GetAnalysisPricesUseCase {

    private final GetAnalysisUseCase getAnalysis;
    private final GetReportUseCase getReport;
    private final GetPriceHistoryUseCase getPrices;

    AnalysisReportService(GetAnalysisUseCase getAnalysis, GetReportUseCase getReport,
                          GetPriceHistoryUseCase getPrices) {
        this.getAnalysis = getAnalysis;
        this.getReport = getReport;
        this.getPrices = getPrices;
    }

    @Override
    public Optional<Report> getReport(AnalysisId id) {
        return key(id).flatMap(getReport::get);
    }

    @Override
    public Optional<PriceHistory> getPrices(AnalysisId id) {
        return key(id).flatMap(getPrices::get);
    }

    private Optional<ReportKey> key(AnalysisId id) {
        Analysis analysis = getAnalysis.get(id);
        return ReportKey.isValidTicker(analysis.spec().ticker())
                ? Optional.of(new ReportKey(analysis.spec().ticker(), analysis.spec().tradeDate()))
                : Optional.empty();
    }
}
