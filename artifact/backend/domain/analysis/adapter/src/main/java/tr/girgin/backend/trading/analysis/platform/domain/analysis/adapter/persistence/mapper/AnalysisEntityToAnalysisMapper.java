package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.entity.AnalysisEntity;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;

@Mapper(
        uses = {
            AnalysisIdEmbeddableToAnalysisIdMapper.class,
            AnalysisSpecEmbeddableToAnalysisSpecMapper.class,
            RunStatsEmbeddableToRunStatsMapper.class
        })
public interface AnalysisEntityToAnalysisMapper {

    // Single-argument transitions on Analysis look like fluent setters to MapStruct.
    @Mapping(target = "withStats", ignore = true)
    Analysis map(AnalysisEntity source);
}
