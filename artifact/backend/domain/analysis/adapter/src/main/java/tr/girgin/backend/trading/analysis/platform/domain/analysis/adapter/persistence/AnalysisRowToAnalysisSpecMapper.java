package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;

@Mapper(uses = StringToAnalystListMapper.class)
interface AnalysisRowToAnalysisSpecMapper {

    AnalysisSpec map(AnalysisRow source);
}
