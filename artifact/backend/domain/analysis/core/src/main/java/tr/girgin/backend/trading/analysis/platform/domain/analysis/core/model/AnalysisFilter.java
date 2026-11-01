package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.util.Set;

/**
 * Optional list criteria; a null field and an empty {@code visibilities} match everything. {@code notOwner}
 * excludes that user's analyses.
 */
public record AnalysisFilter(
        AnalysisStatus status, String ticker, OwnerId owner, Set<Visibility> visibilities, OwnerId notOwner) {}
