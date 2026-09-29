package tr.girgin.backend.trading.analysis.platform.orchestration.report;

import java.util.Optional;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.GetAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.GetReportUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;

@Service
class AnalysisReportService implements GetAnalysisReportUseCase {

    private final GetAnalysisUseCase getAnalysis;
    private final GetReportUseCase getReport;

    AnalysisReportService(GetAnalysisUseCase getAnalysis, GetReportUseCase getReport) {
        this.getAnalysis = getAnalysis;
        this.getReport = getReport;
    }

    @Override
    public Optional<Report> getReport(AnalysisId id) {
        Analysis analysis = getAnalysis.get(id);
        if (!ReportKey.isValidTicker(analysis.spec().ticker())) {
            return Optional.empty();
        }
        return getReport.get(new ReportKey(analysis.spec().ticker(), analysis.spec().tradeDate()));
    }
}
