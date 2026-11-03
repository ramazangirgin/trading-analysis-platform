package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisOwnerDto;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;
import tr.girgin.backend.trading.analysis.platform.orchestration.analysisview.model.AnalysisOwner;
import tr.girgin.backend.trading.analysis.platform.orchestration.analysisview.model.AnalysisView;

@Mapper(
        config = SharedMapperConfig.class,
        uses = {
            AnalysisIdToStringMapper.class,
            RunStatsToRunStatsDtoMapper.class,
            VisibilityToVisibilityEnumDtoMapper.class
        })
public interface AnalysisViewToAnalysisDtoMapper {

    @BeanMapping(ignoreUnmappedSourceProperties = "analysis")
    @Mapping(target = ".", source = "analysis")
    @Mapping(target = "owner", source = "owner")
    @Mapping(target = "canManage", source = "rights.canManage")
    @Mapping(target = "canChangeVisibility", source = "rights.canChangeVisibility")
    @Mapping(target = "allowedVisibilities", source = "rights.allowedVisibilities")
    AnalysisDto map(AnalysisView source);

    @Mapping(target = "id", source = "id.value")
    @Mapping(target = "username", source = "username.value")
    AnalysisOwnerDto map(AnalysisOwner source);
}
