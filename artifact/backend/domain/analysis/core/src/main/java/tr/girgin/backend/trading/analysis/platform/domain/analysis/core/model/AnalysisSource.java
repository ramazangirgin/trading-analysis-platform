package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

/** Where a run came from: started by the platform, or imported from the data dir. */
public enum AnalysisSource {
    PLATFORM,
    EXTERNAL
}
