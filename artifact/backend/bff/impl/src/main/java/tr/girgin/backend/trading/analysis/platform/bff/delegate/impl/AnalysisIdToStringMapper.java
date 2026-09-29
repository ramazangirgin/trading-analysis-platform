package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

@Mapper
interface AnalysisIdToStringMapper {

    default String map(AnalysisId source) {
        return source.value();
    }
}
