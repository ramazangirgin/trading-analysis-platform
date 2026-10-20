package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.entity.AnalysisDefaultsEntity;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.AnalysisDefaults;

@Mapper
public interface AnalysisDefaultsToAnalysisDefaultsEntityMapper {

    @Mapping(target = "id", constant = AnalysisDefaultsEntity.ROW_ID)
    AnalysisDefaultsEntity map(AnalysisDefaults source);
}
