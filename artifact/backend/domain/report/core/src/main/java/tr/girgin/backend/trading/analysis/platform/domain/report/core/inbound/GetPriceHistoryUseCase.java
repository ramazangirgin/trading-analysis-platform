package tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound;

import java.util.Optional;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.PriceHistory;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;

/** Price chart data for a report, read from the price cache upstream wrote. */
public interface GetPriceHistoryUseCase {

    /** Daily prices with moving averages up to the key's trade date, when the cache has them. */
    Optional<PriceHistory> get(ReportKey key);
}
