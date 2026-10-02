package tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound;

import java.util.Optional;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;

/** Reads one report from the data dir, by ticker and trade date. */
public interface GetReportUseCase {

    Optional<Report> get(ReportKey key);
}
