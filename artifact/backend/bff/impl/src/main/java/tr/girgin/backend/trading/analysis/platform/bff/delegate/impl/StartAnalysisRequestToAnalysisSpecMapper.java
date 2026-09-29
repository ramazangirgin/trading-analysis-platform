package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.StartAnalysisRequest;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;

@Mapper
interface StartAnalysisRequestToAnalysisSpecMapper {

    @Mapping(target = "assetType", defaultValue = "STOCK")
    @Mapping(target = "maxDebateRounds", defaultValue = "1")
    @Mapping(target = "maxRiskDiscussRounds", defaultValue = "1")
    @Mapping(target = "outputLanguage", defaultValue = "English")
    @Mapping(target = "checkpointEnabled", defaultValue = "false")
    AnalysisSpec map(StartAnalysisRequest source);
}
