package tr.girgin.backend.trading.analysis.platform.domain.report.core.outbound.prices;

import java.time.LocalDate;
import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.PriceBar;

/** Reads upstream's daily price cache (the files its data tools download). Never writes to it. */
public interface PriceCachePort {

    /**
     * The ticker's daily bars from the cache file that covers {@code upTo} best, oldest first;
     * empty when the cache has nothing for the ticker.
     */
    List<PriceBar> read(String ticker, LocalDate upTo);
}
