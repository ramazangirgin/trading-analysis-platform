package tr.girgin.backend.trading.analysis.platform.orchestration.preview.inbound;

import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.ListPartialAnalysesPort;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.PartialAnalysisPage;
import tr.girgin.backend.trading.analysis.platform.orchestration.preview.model.AnalysisPreviewPage;

@Service
public class ListAnalysisPreviewsUseCase {

    private final ListPartialAnalysesPort listPartialAnalysesPort;
    private final PreviewCutter cutter;

    ListAnalysisPreviewsUseCase(ListPartialAnalysesPort listPartialAnalysesPort, PreviewCutter cutter) {
        this.listPartialAnalysesPort = listPartialAnalysesPort;
        this.cutter = cutter;
    }

    public AnalysisPreviewPage list(int page, int size) {
        PartialAnalysisPage partial = listPartialAnalysesPort.list(page, size);
        return new AnalysisPreviewPage(
                partial.items().stream().map(cutter::summary).toList(), partial.hasMore());
    }
}
