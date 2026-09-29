package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

/** Optional list criteria; a null field matches everything. */
public record AnalysisFilter(AnalysisStatus status, String ticker) {

    public static final AnalysisFilter ALL = new AnalysisFilter(null, null);
}
