package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

/** Identifies one analysis run. Also used as the run directory name, so its format is checked by the services. */
public record AnalysisId(String value) {}
