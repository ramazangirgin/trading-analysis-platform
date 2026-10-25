package tr.girgin.backend.trading.analysis.platform.orchestration.preview.inbound;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.GetPartialAnalysisUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.orchestration.preview.model.AnalysisPreview;

@Service
@EnableConfigurationProperties(PreviewSettings.class)
public class GetAnalysisPreviewUseCase {

    private final GetPartialAnalysisUseCase getPartialAnalysis;
    private final PreviewCutter cutter;

    GetAnalysisPreviewUseCase(GetPartialAnalysisUseCase getPartialAnalysis, PreviewCutter cutter) {
        this.getPartialAnalysis = getPartialAnalysis;
        this.cutter = cutter;
    }

    /**
     * The preview of a partial analysis; anything else, a private or public analysis included, is reported as
     * not found. A report that is missing gives a preview without sections.
     */
    public AnalysisPreview get(AnalysisId id) {
        return cutter.preview(getPartialAnalysis.get(id));
    }
}
