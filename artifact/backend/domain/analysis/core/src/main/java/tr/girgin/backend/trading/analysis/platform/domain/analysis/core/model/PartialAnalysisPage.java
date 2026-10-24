package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.util.List;

public record PartialAnalysisPage(List<Analysis> items, boolean hasMore) {

    public PartialAnalysisPage {
        items = List.copyOf(items);
    }
}
