package tr.girgin.backend.trading.analysis.platform.orchestration.report.inbound;

import java.util.Optional;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.GetAnalysisPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.GetReportPort;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;

@Service
public class GetAnalysisReportUseCase {

    private final GetAnalysisPort getAnalysisPort;
    private final GetReportPort getReportPort;

    GetAnalysisReportUseCase(GetAnalysisPort getAnalysisPort, GetReportPort getReportPort) {
        this.getAnalysisPort = getAnalysisPort;
        this.getReportPort = getReportPort;
    }

    /**
     * The data dir report for an analysis' ticker and date. Several runs of the same ticker and
     * date share these files (upstream overwrites them), so this is the latest one's output.
     * An analysis the caller may not see is reported as not found.
     */
    public Optional<Report> getReport(AnalysisId id, AnalysisAccess access) {
        Analysis analysis = getAnalysisPort.get(id, access);
        return getReportPort.get(
                new ReportKey(analysis.spec().ticker(), analysis.spec().tradeDate()));
    }
}
