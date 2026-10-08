package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.entity.AnalysisIdEmbeddable;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

/** An analysis ID from its entity key, mapped by its {@code value}. */
@Mapper
public interface AnalysisIdEmbeddableToAnalysisIdMapper {

    AnalysisId map(AnalysisIdEmbeddable source);
}
