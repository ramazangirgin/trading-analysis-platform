package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.row.AnalysisRow;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.RunStats;

@Mapper(uses = MillisToDurationMapper.class)
public interface AnalysisRowToRunStatsMapper {

    @Mapping(target = "elapsed", source = "elapsedMs")
    RunStats map(AnalysisRow source);
}
