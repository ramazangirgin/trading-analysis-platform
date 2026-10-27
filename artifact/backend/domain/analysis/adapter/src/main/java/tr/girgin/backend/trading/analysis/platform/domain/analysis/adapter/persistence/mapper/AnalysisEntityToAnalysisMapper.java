package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.entity.AnalysisEntity;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

@Mapper(
        config = SharedMapperConfig.class,
        uses = {
            AnalysisIdEmbeddableToAnalysisIdMapper.class,
            AnalysisSpecEmbeddableToAnalysisSpecMapper.class,
            RunStatsEmbeddableToRunStatsMapper.class
        })
public interface AnalysisEntityToAnalysisMapper {

    // Single-argument transitions on Analysis look like fluent setters to MapStruct.
    @BeanMapping(ignoreUnmappedSourceProperties = "new")
    @Mapping(target = "withStats", ignore = true)
    @Mapping(target = "withVisibilityOf", ignore = true)
    Analysis map(AnalysisEntity source);
}
