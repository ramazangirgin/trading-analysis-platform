package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisPreviewDto;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;
import tr.girgin.backend.trading.analysis.platform.orchestration.preview.model.AnalysisPreview;

@Mapper(config = SharedMapperConfig.class, uses = ReportSectionToStringMapper.class)
public interface AnalysisPreviewToAnalysisPreviewDtoMapper {

    @Mapping(target = "id", source = "id.value")
    AnalysisPreviewDto map(AnalysisPreview source);
}
