package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunStats;

@Mapper(uses = MillisToDurationMapper.class)
interface AnalysisRowToRunStatsMapper {

    @Mapping(target = "elapsed", source = "elapsedMs")
    RunStats map(AnalysisRow source);
}
