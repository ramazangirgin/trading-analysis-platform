package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.row.AnalysisRow;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;

@Mapper(uses = StringToAnalystListMapper.class)
public interface AnalysisRowToAnalysisSpecMapper {

    AnalysisSpec map(AnalysisRow source);
}
