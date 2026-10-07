package tr.girgin.backend.trading.analysis.platform.orchestration.report.inbound;

import tr.girgin.backend.trading.analysis.platform.orchestration.report.model.ImportResult;

public interface ImportExistingRunsUseCase {

    /** Registers every run found in the data dir as an EXTERNAL analysis. */
    ImportResult importExistingRuns();
}
