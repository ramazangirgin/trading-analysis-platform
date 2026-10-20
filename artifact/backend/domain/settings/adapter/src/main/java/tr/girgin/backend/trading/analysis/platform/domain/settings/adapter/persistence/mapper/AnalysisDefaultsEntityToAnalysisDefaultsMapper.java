package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.entity.AnalysisDefaultsEntity;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.AnalysisDefaults;

@Mapper
public interface AnalysisDefaultsEntityToAnalysisDefaultsMapper {

    AnalysisDefaults map(AnalysisDefaultsEntity source);
}
