package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.ReportsApiDelegate;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.ImportResultDto;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.RequiresPermission;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.ImportResultToImportResultDtoMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.orchestration.report.inbound.ImportExistingRunsUseCase;

@Service
class ReportsApiDelegateImpl implements ReportsApiDelegate {

    private final ImportExistingRunsUseCase importExistingRunsUseCase;
    private final ImportResultToImportResultDtoMapper importResultToImportResultDtoMapper;

    ReportsApiDelegateImpl(
            ImportExistingRunsUseCase importExistingRunsUseCase,
            ImportResultToImportResultDtoMapper importResultToImportResultDtoMapper) {
        this.importExistingRunsUseCase = importExistingRunsUseCase;
        this.importResultToImportResultDtoMapper = importResultToImportResultDtoMapper;
    }

    @Override
    // It creates and updates analyses from the data folder, as runs do.
    @RequiresPermission(Permission.ANALYSIS_RUN)
    public ImportResultDto rescan() {
        return importResultToImportResultDtoMapper.map(importExistingRunsUseCase.importExistingRuns());
    }
}
