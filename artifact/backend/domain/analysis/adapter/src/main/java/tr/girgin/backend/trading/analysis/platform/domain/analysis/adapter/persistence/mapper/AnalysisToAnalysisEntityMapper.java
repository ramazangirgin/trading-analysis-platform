package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.entity.AnalysisEntity;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

@Mapper(
        config = SharedMapperConfig.class,
        uses = {
            AnalysisIdToAnalysisIdEmbeddableMapper.class,
            AnalysisSpecToAnalysisSpecEmbeddableMapper.class,
            RunStatsToRunStatsEmbeddableMapper.class
        })
public interface AnalysisToAnalysisEntityMapper {

    AnalysisEntity map(Analysis source);
}
