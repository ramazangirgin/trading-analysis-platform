package tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound;

import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.Report;

/** Lists the reports in the data dir. */
public interface ScanReportsUseCase {

    /** Every report in the data dir, newest trade date first. Read-only: nothing is changed on disk. */
    List<Report> scan();
}
