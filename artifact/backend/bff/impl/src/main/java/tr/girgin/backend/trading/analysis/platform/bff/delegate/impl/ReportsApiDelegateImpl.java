package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.ReportsApiDelegate;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.ImportResultDto;
import tr.girgin.backend.trading.analysis.platform.orchestration.report.ImportExistingRunsUseCase;

@Service
class ReportsApiDelegateImpl implements ReportsApiDelegate {

    private final ImportExistingRunsUseCase importExistingRuns;
    private final ImportResultToImportResultDtoMapper mapper;

    ReportsApiDelegateImpl(ImportExistingRunsUseCase importExistingRuns, ImportResultToImportResultDtoMapper mapper) {
        this.importExistingRuns = importExistingRuns;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<ImportResultDto> rescan() {
        return ResponseEntity.ok(mapper.map(importExistingRuns.importExistingRuns()));
    }
}
