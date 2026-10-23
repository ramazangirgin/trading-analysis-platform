package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

/** Optional list criteria; a null field matches everything. {@code notOwner} excludes that user's analyses. */
public record AnalysisFilter(
        AnalysisStatus status, String ticker, OwnerId owner, Visibility visibility, OwnerId notOwner) {

    public static final AnalysisFilter ALL = new AnalysisFilter(null, null, null);

    public AnalysisFilter(AnalysisStatus status, String ticker, OwnerId owner) {
        this(status, ticker, owner, null, null);
    }

    public AnalysisFilter ownedBy(OwnerId user) {
        return new AnalysisFilter(status, ticker, user);
    }

    public AnalysisFilter publicOfOthersThan(OwnerId user) {
        return new AnalysisFilter(status, ticker, null, Visibility.PUBLIC, user);
    }
}
