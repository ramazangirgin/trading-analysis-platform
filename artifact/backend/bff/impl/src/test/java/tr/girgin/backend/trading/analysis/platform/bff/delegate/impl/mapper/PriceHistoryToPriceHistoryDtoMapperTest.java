package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PriceHistoryDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PricePointDto;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.PriceHistory;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.PricePoint;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.ReportKey;

class PriceHistoryToPriceHistoryDtoMapperTest {

    private final PriceHistoryToPriceHistoryDtoMapper priceHistoryToPriceHistoryDtoMapper =
            new PriceHistoryToPriceHistoryDtoMapperImpl();

    @Test
    void mapsTickerTradeDateAndPointsInOrder() {
        LocalDate tradeDate = LocalDate.of(2026, 3, 2);
        PriceHistory history = new PriceHistory(
                new ReportKey("NVDA", tradeDate),
                List.of(
                        new PricePoint(LocalDate.of(2026, 2, 27), 10.5, 1000L, 10.1, 9.9, 9.5),
                        new PricePoint(LocalDate.of(2026, 3, 2), 11.5, 2000L, 10.6, 10.0, 9.6)));

        PriceHistoryDto dto = priceHistoryToPriceHistoryDtoMapper.map(history);

        assertThat(dto.ticker()).isEqualTo("NVDA");
        assertThat(dto.tradeDate()).isEqualTo(tradeDate);
        assertThat(dto.points())
                .containsExactly(
                        new PricePointDto(LocalDate.of(2026, 2, 27), 10.5, 1000L, 10.1, 9.9, 9.5),
                        new PricePointDto(LocalDate.of(2026, 3, 2), 11.5, 2000L, 10.6, 10.0, 9.6));
    }
}
