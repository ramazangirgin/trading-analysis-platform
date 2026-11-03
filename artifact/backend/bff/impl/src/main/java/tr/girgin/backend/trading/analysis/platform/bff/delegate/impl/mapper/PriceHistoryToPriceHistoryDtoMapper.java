package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PriceHistoryDto;
import tr.girgin.backend.trading.analysis.platform.domain.report.core.model.PriceHistory;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

@Mapper(config = SharedMapperConfig.class)
public interface PriceHistoryToPriceHistoryDtoMapper {

    @Mapping(target = "ticker", source = "key.ticker")
    @Mapping(target = "tradeDate", source = "key.tradeDate")
    PriceHistoryDto map(PriceHistory source);
}
