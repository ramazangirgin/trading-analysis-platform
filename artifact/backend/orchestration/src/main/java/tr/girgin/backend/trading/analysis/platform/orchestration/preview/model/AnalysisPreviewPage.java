package tr.girgin.backend.trading.analysis.platform.orchestration.preview.model;

import java.util.List;

public record AnalysisPreviewPage(List<AnalysisPreviewSummary> items, boolean hasMore) {

    public AnalysisPreviewPage {
        items = List.copyOf(items);
    }
}
