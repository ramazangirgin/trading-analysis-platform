package tr.girgin.backend.trading.analysis.platform.orchestration.report;

public interface ImportExistingRunsUseCase {

    /** Registers every run found in the data dir as an EXTERNAL analysis (PLAN.md section 3.6). */
    ImportResult importExistingRuns();
}
