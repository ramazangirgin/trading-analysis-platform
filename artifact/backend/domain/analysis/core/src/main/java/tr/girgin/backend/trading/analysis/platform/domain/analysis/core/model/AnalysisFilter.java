package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.util.Set;

/**
 * Optional list criteria; a null field and an empty {@code visibilities} match everything. {@code notOwner}
 * excludes that user's analyses.
 */
public record AnalysisFilter(
        AnalysisStatus status, String ticker, OwnerId owner, Set<Visibility> visibilities, OwnerId notOwner) {

    public static final AnalysisFilter ALL = new AnalysisFilter(null, null, null);

    public AnalysisFilter {
        visibilities = visibilities == null ? Set.of() : Set.copyOf(visibilities);
    }

    public AnalysisFilter(AnalysisStatus status, String ticker, OwnerId owner) {
        this(status, ticker, owner, Set.of(), null);
    }

    public static AnalysisFilter partial() {
        return new AnalysisFilter(AnalysisStatus.COMPLETED, null, null, Set.of(Visibility.PARTIAL), null);
    }

    public AnalysisFilter ownedBy(OwnerId user) {
        return new AnalysisFilter(status, ticker, user);
    }

    public AnalysisFilter publicOfOthersThan(OwnerId user) {
        return new AnalysisFilter(status, ticker, null, Set.of(Visibility.PUBLIC, Visibility.PARTIAL), user);
    }
}
