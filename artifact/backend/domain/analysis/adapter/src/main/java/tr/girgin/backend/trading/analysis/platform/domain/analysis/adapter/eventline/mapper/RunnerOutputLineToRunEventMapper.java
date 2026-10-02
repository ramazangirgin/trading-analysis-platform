package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline.json.RunnerOutputLine;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunEvent;

@Mapper(uses = {
        StringToInstantMapper.class,
        StringToRunEventTypeMapper.class,
        StringToRatingMapper.class,
        StringToRunOutcomeMapper.class,
        RunnerOutputLineToRunStatsMapper.class})
public interface RunnerOutputLineToRunEventMapper {

    @Mapping(target = "timestamp", source = "ts")
    @Mapping(target = "outcome", source = "status")
    @Mapping(target = "stats", source = ".")
    RunEvent map(RunnerOutputLine source);
}
