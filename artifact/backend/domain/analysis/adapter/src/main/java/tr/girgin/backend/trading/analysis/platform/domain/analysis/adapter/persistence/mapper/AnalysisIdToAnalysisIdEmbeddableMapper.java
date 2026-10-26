package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.entity.AnalysisIdEmbeddable;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

/** The entity key of an analysis ID, mapped by its {@code value}. */
@Mapper(config = SharedMapperConfig.class)
public interface AnalysisIdToAnalysisIdEmbeddableMapper {

    AnalysisIdEmbeddable map(AnalysisId source);
}
