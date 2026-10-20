package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.StartAnalysisRequest;
import tr.girgin.backend.trading.analysis.platform.orchestration.analysisstart.model.RequestedAnalysis;

@Mapper
public interface StartAnalysisRequestToRequestedAnalysisMapper {

    RequestedAnalysis map(StartAnalysisRequest source);
}
