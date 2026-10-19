package tr.girgin.backend.trading.analysis.platform.orchestration.report.inbound;

import java.util.Optional;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;

public interface GetAnalysisReportUseCase {

    /**
     * The data dir report for an analysis' ticker and date. Several runs of the same ticker and
     * date share these files (upstream overwrites them), so this is the latest one's output.
     * An analysis the caller may not see is reported as not found.
     */
    Optional<Report> getReport(AnalysisId id, AnalysisAccess access);
}
