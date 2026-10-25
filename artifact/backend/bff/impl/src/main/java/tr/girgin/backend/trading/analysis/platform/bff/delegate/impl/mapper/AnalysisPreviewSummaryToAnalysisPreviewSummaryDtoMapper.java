package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisPreviewSummaryDto;
import tr.girgin.backend.trading.analysis.platform.orchestration.preview.model.AnalysisPreviewSummary;

@Mapper(uses = AnalysisIdToStringMapper.class)
public interface AnalysisPreviewSummaryToAnalysisPreviewSummaryDtoMapper {

    AnalysisPreviewSummaryDto map(AnalysisPreviewSummary source);
}
