package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.StartAnalysisRequest;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;
import tr.girgin.backend.trading.analysis.platform.orchestration.analysisstart.model.RequestedAnalysis;

@Mapper(config = SharedMapperConfig.class)
public interface StartAnalysisRequestToRequestedAnalysisMapper {

    RequestedAnalysis map(StartAnalysisRequest source);
}
