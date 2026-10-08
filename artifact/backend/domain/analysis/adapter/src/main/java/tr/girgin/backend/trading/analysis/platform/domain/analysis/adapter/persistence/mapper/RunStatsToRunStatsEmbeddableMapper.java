package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.entity.RunStatsEmbeddable;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunStats;
import tr.girgin.backend.trading.analysis.platform.library.mapper.DurationToMillisMapper;

@Mapper(uses = DurationToMillisMapper.class)
public interface RunStatsToRunStatsEmbeddableMapper {

    @Mapping(target = "elapsedMs", source = "elapsed")
    RunStatsEmbeddable map(RunStats source);
}
