package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import java.util.List;
import java.util.Map;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisOwnerDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.VisibilityEnumDto;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.AnalysisRights;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

@Mapper(
        config = SharedMapperConfig.class,
        uses = {
            AnalysisIdToStringMapper.class,
            RunStatsToRunStatsDtoMapper.class,
            VisibilityToVisibilityEnumDtoMapper.class
        })
public interface AnalysisToAnalysisDtoMapper {

    @Mapping(target = "canManage", expression = "java(rights.canManage())")
    @Mapping(target = "canChangeVisibility", expression = "java(!rights.allowedVisibilities().isEmpty())")
    @Mapping(target = "allowedVisibilities", source = ".")
    AnalysisDto map(Analysis source, @Context Map<UserId, Username> usernames, @Context AnalysisRights rights);

    default List<VisibilityEnumDto> map(Analysis source, @Context AnalysisRights rights) {
        return rights.allowedVisibilities().stream()
                .map(visibility -> VisibilityEnumDto.valueOf(visibility.name()))
                .toList();
    }

    default AnalysisOwnerDto map(OwnerId source, @Context Map<UserId, Username> usernames) {
        if (source == null) {
            return null;
        }
        Username username = usernames.get(new UserId(source.value()));
        return new AnalysisOwnerDto(source.value(), username == null ? null : username.value());
    }
}
