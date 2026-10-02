package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PriceHistoryDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PricePointDto;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.PriceHistory;

@Mapper
public interface PriceHistoryToPriceHistoryDtoMapper {

    default PriceHistoryDto map(PriceHistory source) {
        return new PriceHistoryDto(
                source.key().ticker(),
                source.key().tradeDate(),
                source.points().stream()
                        .map(p -> new PricePointDto(p.date(), p.close(), p.volume(), p.ema10(), p.sma50(), p.sma200()))
                        .toList());
    }
}
