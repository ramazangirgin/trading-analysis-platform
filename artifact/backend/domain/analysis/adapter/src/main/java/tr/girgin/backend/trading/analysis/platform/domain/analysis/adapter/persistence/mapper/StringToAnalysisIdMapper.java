package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

@Mapper
public interface StringToAnalysisIdMapper {

    default AnalysisId map(String source) {
        return new AnalysisId(source);
    }
}
