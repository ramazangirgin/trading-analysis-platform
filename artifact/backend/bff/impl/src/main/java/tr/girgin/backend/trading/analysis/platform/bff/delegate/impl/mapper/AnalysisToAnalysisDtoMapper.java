package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import java.util.Map;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisOwnerDto;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisAccess;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.OwnerId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;

@Mapper(
        uses = {
            AnalysisIdToStringMapper.class,
            RunStatsToRunStatsDtoMapper.class,
            VisibilityToVisibilityEnumDtoMapper.class
        })
public interface AnalysisToAnalysisDtoMapper {

    @Mapping(target = "canManage", expression = "java(access.canManage(source))")
    @Mapping(target = "canChangeVisibility", expression = "java(!access.allowedVisibilities(source).isEmpty())")
    AnalysisDto map(Analysis source, @Context Map<UserId, Username> usernames, @Context AnalysisAccess access);

    default AnalysisOwnerDto map(OwnerId source, @Context Map<UserId, Username> usernames) {
        if (source == null) {
            return null;
        }
        Username username = usernames.get(new UserId(source.value()));
        return new AnalysisOwnerDto(source.value(), username == null ? null : username.value());
    }
}
