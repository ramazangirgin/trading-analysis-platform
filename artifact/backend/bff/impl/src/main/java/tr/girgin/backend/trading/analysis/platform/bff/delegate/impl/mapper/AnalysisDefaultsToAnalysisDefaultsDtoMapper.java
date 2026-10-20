package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisDefaultsDto;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.AnalysisDefaults;

@Mapper
public interface AnalysisDefaultsToAnalysisDefaultsDtoMapper {

    AnalysisDefaultsDto map(AnalysisDefaults source);
}
