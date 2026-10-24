package tr.girgin.backend.trading.analysis.platform.orchestration.preview.inbound;

import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.ListPartialAnalysesUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.PartialAnalysisPage;
import tr.girgin.backend.trading.analysis.platform.orchestration.preview.model.AnalysisPreviewPage;

@Service
public class ListAnalysisPreviewsUseCase {

    private final ListPartialAnalysesUseCase listPartialAnalyses;
    private final PreviewCutter cutter;

    ListAnalysisPreviewsUseCase(ListPartialAnalysesUseCase listPartialAnalyses, PreviewCutter cutter) {
        this.listPartialAnalyses = listPartialAnalyses;
        this.cutter = cutter;
    }

    /** Newest first; {@code page} counts from 0. */
    public AnalysisPreviewPage list(int page, int size) {
        PartialAnalysisPage partial = listPartialAnalyses.list(page, size);
        return new AnalysisPreviewPage(
                partial.items().stream().map(cutter::summary).toList(), partial.hasMore());
    }
}
