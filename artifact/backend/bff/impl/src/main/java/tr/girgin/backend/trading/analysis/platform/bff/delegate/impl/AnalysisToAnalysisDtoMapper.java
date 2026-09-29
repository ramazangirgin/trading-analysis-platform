package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisDto;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;

@Mapper(uses = {AnalysisIdToStringMapper.class, RunStatsToRunStatsDtoMapper.class})
interface AnalysisToAnalysisDtoMapper {

    AnalysisDto map(Analysis source);
}
