package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisScopeEnumDto;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisScope;

@Mapper
public interface AnalysisScopeEnumDtoToAnalysisScopeMapper {

    AnalysisScope map(AnalysisScopeEnumDto source);
}
