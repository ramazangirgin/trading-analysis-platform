package tr.girgin.backend.trading.analysis.platform.domain.report.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.PriceBar;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.PriceHistory;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.PricePoint;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;

class PriceHistoryServiceTest {

    private static final LocalDate START = LocalDate.of(2025, 1, 1);

    private static PriceBar bar(int day, double close) {
        return new PriceBar(START.plusDays(day), close, close, close, close, 1000L + day);
    }

    @Test
    void emaMatchesPandasAdjustedEwm() {
        // pandas: Series([10, 11, 12, 11, 13, 14]).ewm(span=3, adjust=True, min_periods=1).mean()
        assertThat(PriceHistoryService.ema(new double[] {10, 11, 12, 11, 13, 14}, 3)).containsExactly(
                new double[] {10.0, 10.6666666667, 11.4285714286, 11.2, 12.1290322581, 13.0793650794},
                within(1e-9));
    }

    @Test
    void smaMatchesPandasRollingMeanWithPartialWindows() {
        // pandas: Series([10, 11, 12, 11, 13, 14]).rolling(2, min_periods=1).mean()
        assertThat(PriceHistoryService.sma(new double[] {10, 11, 12, 11, 13, 14}, 2))
                .containsExactly(new double[] {10.0, 10.5, 11.5, 11.5, 12.0, 13.5}, within(1e-12));
    }

    @Test
    void returnsTheLastYearUpToTheTradeDateWithAveragesOverTheWholeHistory() {
        List<PriceBar> bars = IntStream.range(0, 400).mapToObj(day -> bar(day, day)).toList();
        LocalDate tradeDate = START.plusDays(299);
        PriceHistoryService service = new PriceHistoryService((ticker, upTo) -> bars);

        PriceHistory history = service.get(new ReportKey("MU", tradeDate)).orElseThrow();

        assertThat(history.points()).hasSize(PriceHistoryService.DAYS_SHOWN);
        PricePoint last = history.points().getLast();
        assertThat(last.date()).isEqualTo(tradeDate);
        assertThat(last.close()).isEqualTo(299);
        assertThat(last.sma50()).isEqualTo((250 + 299) / 2.0);
        // 300 days up to the trade date, the last 252 shown: the first is day 48, whose 200-day
        // average covers days 0-48 (24), not just itself (48).
        PricePoint first = history.points().getFirst();
        assertThat(first.date()).isEqualTo(START.plusDays(48));
        assertThat(first.sma200()).isEqualTo(24.0);
    }

    @Test
    void isEmptyWithoutCachedPrices() {
        PriceHistoryService service = new PriceHistoryService((ticker, upTo) -> List.of());
        assertThat(service.get(new ReportKey("MU", START))).isEmpty();
    }
}
