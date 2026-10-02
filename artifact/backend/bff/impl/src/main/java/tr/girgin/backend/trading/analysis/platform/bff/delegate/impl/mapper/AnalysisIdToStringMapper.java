package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

@Mapper
public interface AnalysisIdToStringMapper {

    default String map(AnalysisId source) {
        return source.value();
    }
}
