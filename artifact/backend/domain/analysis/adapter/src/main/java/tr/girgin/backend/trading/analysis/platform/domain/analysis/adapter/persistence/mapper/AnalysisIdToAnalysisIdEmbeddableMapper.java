package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.entity.AnalysisIdEmbeddable;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

/** The entity key of an analysis ID, mapped by its {@code value}. */
@Mapper
public interface AnalysisIdToAnalysisIdEmbeddableMapper {

    AnalysisIdEmbeddable map(AnalysisId source);
}
