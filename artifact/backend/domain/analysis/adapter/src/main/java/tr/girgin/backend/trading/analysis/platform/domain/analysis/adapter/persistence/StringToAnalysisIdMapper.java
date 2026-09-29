package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

@Mapper
interface StringToAnalysisIdMapper {

    default AnalysisId map(String source) {
        return new AnalysisId(source);
    }
}
