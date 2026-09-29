package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

/** How a run ended, as reported by the runner. */
public enum RunOutcome {
    COMPLETED, STOPPED, FAILED;

    public AnalysisStatus toStatus() {
        return switch (this) {
            case COMPLETED -> AnalysisStatus.COMPLETED;
            case STOPPED -> AnalysisStatus.STOPPED;
            case FAILED -> AnalysisStatus.FAILED;
        };
    }
}
