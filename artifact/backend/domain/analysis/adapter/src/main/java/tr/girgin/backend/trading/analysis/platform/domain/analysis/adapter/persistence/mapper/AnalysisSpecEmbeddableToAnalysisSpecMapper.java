package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.entity.AnalysisSpecEmbeddable;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;

@Mapper
public interface AnalysisSpecEmbeddableToAnalysisSpecMapper {

    AnalysisSpec map(AnalysisSpecEmbeddable source);
}
