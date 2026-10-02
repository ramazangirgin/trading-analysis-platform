package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.ImportResultDto;
import tr.girgin.backend.trading.analysis.platform.orchestration.report.model.ImportResult;

@Mapper
public interface ImportResultToImportResultDtoMapper {

    ImportResultDto map(ImportResult source);
}
