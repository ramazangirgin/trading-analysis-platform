package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

public enum AnalysisStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    STOPPED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == STOPPED || this == FAILED;
    }
}
