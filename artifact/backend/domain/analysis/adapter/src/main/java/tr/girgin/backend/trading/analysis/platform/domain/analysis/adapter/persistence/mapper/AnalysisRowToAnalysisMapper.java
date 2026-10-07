package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.row.AnalysisRow;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;

@Mapper(
        uses = {
            StringToAnalysisIdMapper.class,
            OffsetDateTimeToInstantMapper.class,
            AnalysisRowToAnalysisSpecMapper.class,
            AnalysisRowToRunStatsMapper.class
        })
public interface AnalysisRowToAnalysisMapper {

    // Single-argument transitions on Analysis look like fluent setters to MapStruct.
    @Mapping(target = "withStats", ignore = true)
    @Mapping(target = "spec", source = ".")
    @Mapping(target = "stats", source = ".")
    Analysis map(AnalysisRow source);
}
