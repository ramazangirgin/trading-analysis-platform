package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.ImportResultDto;
import tr.girgin.backend.trading.analysis.platform.orchestration.report.ImportResult;

@Mapper
interface ImportResultToImportResultDtoMapper {

    ImportResultDto map(ImportResult source);
}
