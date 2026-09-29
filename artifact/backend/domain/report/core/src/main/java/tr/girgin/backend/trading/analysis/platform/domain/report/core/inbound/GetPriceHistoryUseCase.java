package tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound;

import java.util.Optional;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.PriceHistory;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;

public interface GetPriceHistoryUseCase {

    /** Daily prices with moving averages up to the key's trade date, when the cache has them. */
    Optional<PriceHistory> get(ReportKey key);
}
