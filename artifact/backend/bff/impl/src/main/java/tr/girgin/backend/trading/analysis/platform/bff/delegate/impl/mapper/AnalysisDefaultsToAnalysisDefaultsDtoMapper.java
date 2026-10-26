package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisDefaultsDto;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.AnalysisDefaults;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

@Mapper(config = SharedMapperConfig.class)
public interface AnalysisDefaultsToAnalysisDefaultsDtoMapper {

    AnalysisDefaultsDto map(AnalysisDefaults source);
}
