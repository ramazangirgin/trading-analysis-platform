package tr.girgin.backend.trading.analysis.platform.domain.report.core.outbound.history;

import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.RunHistoryEntry;

/** Reads a third-party UI's run history. Never writes to it. */
public interface RunHistoryPort {

    /** Every finished run with a valid ticker and date; runs still in progress are left out. */
    List<RunHistoryEntry> readAll();
}
