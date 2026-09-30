package tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound;

import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.RunHistoryEntry;

public interface ScanRunHistoryUseCase {

    /** Every finished run in the data dir's run history, newest first. Read-only. */
    List<RunHistoryEntry> scan();
}
