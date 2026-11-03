package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper;

import org.mapstruct.Mapper;
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

    Analysis map(AnalysisEntity source);
}
