package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RunStatsDto;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunStats;
import tr.girgin.backend.trading.analysis.platform.library.mapper.DurationToMillisMapper;

@Mapper(uses = DurationToMillisMapper.class)
public interface RunStatsToRunStatsDtoMapper {

    @Mapping(target = "elapsedMs", source = "elapsed")
    RunStatsDto map(RunStats source);
}
