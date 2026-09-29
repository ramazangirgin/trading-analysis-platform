package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisStatusDto;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisStatus;

@Mapper
interface AnalysisStatusDtoToAnalysisStatusMapper {

    AnalysisStatus map(AnalysisStatusDto source);
}
