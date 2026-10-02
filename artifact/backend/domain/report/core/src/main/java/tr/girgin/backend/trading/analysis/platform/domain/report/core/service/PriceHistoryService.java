package tr.girgin.backend.trading.analysis.platform.domain.report.core.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound.GetPriceHistoryUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.PriceBar;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.PriceHistory;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.PricePoint;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.outbound.prices.PriceCachePort;

/**
 * The price chart above the market report (KI-4). The averages are computed over the whole cached
 * history, as upstream's stockstats does, so they match the values the market analyst quotes; only
 * the last year up to the trade date is returned.
 */
@Service
class PriceHistoryService implements GetPriceHistoryUseCase {

    static final int DAYS_SHOWN = 252;
    // Moving-average periods, in trading days.
    private static final int EMA_SHORT = 10;
    private static final int SMA_MEDIUM = 50;
    private static final int SMA_LONG = 200;

    private final PriceCachePort cache;

    PriceHistoryService(PriceCachePort cache) {
        this.cache = cache;
    }

    @Override
    public Optional<PriceHistory> get(ReportKey key) {
        List<PriceBar> bars = cache.read(key.ticker(), key.tradeDate()).stream()
                .filter(bar -> !bar.date().isAfter(key.tradeDate()))
                .sorted(Comparator.comparing(PriceBar::date))
                .toList();
        if (bars.isEmpty()) {
            return Optional.empty();
        }
        double[] closes = bars.stream().mapToDouble(PriceBar::close).toArray();
        double[] ema10 = ema(closes, EMA_SHORT);
        double[] sma50 = sma(closes, SMA_MEDIUM);
        double[] sma200 = sma(closes, SMA_LONG);
        List<PricePoint> points = new ArrayList<>();
        for (int i = Math.max(0, bars.size() - DAYS_SHOWN); i < bars.size(); i++) {
            PriceBar bar = bars.get(i);
            points.add(new PricePoint(bar.date(), bar.close(), bar.volume(), ema10[i], sma50[i], sma200[i]));
        }
        return Optional.of(new PriceHistory(key, points));
    }

    /** pandas {@code ewm(span, adjust=True, min_periods=1).mean()}, stockstats' EMA. */
    static double[] ema(double[] values, int span) {
        double decay = 1 - 2.0 / (span + 1);
        double[] result = new double[values.length];
        double weighted = 0;
        double weights = 0;
        for (int i = 0; i < values.length; i++) {
            weighted = values[i] + decay * weighted;
            weights = 1 + decay * weights;
            result[i] = weighted / weights;
        }
        return result;
    }

    /** pandas {@code rolling(window, min_periods=1).mean()}, stockstats' SMA. */
    static double[] sma(double[] values, int window) {
        double[] result = new double[values.length];
        double sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i];
            if (i >= window) {
                sum -= values[i - window];
            }
            result[i] = sum / Math.min(i + 1, window);
        }
        return result;
    }
}
