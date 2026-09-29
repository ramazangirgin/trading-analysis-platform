package tr.girgin.backend.trading.analysis.platform.domain.report.core.outbound.datadir;

import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportContent;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;

/** Reads upstream's results directory. Never writes to it. */
public interface DataDirPort {

    /** Every readable source file, one entry per file. Unreadable files are skipped. */
    List<ReportContent> readAll();

    /** The sources for one ticker and date. */
    List<ReportContent> read(ReportKey key);
}
