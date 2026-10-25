package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.PublicAnalysesApiDelegate;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisPreviewDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisPreviewPageDto;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.NoPermissionRequired;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.AnalysisPreviewSummaryToAnalysisPreviewSummaryDtoMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.AnalysisPreviewToAnalysisPreviewDtoMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.StringToAnalysisIdMapper;
import tr.girgin.backend.trading.analysis.platform.orchestration.preview.inbound.GetAnalysisPreviewUseCase;
import tr.girgin.backend.trading.analysis.platform.orchestration.preview.inbound.ListAnalysisPreviewsUseCase;
import tr.girgin.backend.trading.analysis.platform.orchestration.preview.model.AnalysisPreviewPage;

@Service
class PublicAnalysesApiDelegateImpl implements PublicAnalysesApiDelegate {

    private final ListAnalysisPreviewsUseCase listPreviews;
    private final GetAnalysisPreviewUseCase getPreview;
    private final AnalysisPreviewSummaryToAnalysisPreviewSummaryDtoMapper summaryMapper;
    private final AnalysisPreviewToAnalysisPreviewDtoMapper previewMapper;
    private final StringToAnalysisIdMapper idMapper;

    PublicAnalysesApiDelegateImpl(
            ListAnalysisPreviewsUseCase listPreviews,
            GetAnalysisPreviewUseCase getPreview,
            AnalysisPreviewSummaryToAnalysisPreviewSummaryDtoMapper summaryMapper,
            AnalysisPreviewToAnalysisPreviewDtoMapper previewMapper,
            StringToAnalysisIdMapper idMapper) {
        this.listPreviews = listPreviews;
        this.getPreview = getPreview;
        this.summaryMapper = summaryMapper;
        this.previewMapper = previewMapper;
        this.idMapper = idMapper;
    }

    @Override
    @NoPermissionRequired("open to visitors: the preview of the partial analyses, cut on the server")
    public AnalysisPreviewPageDto listPublicAnalyses(int page, int size) {
        AnalysisPreviewPage previews = listPreviews.list(page, size);
        return new AnalysisPreviewPageDto(
                previews.items().stream().map(summaryMapper::map).toList(), previews.hasMore());
    }

    @Override
    @NoPermissionRequired("open to visitors: the preview of one partial analysis, cut on the server")
    public AnalysisPreviewDto getPublicAnalysis(String id) {
        return previewMapper.map(getPreview.get(idMapper.map(id)));
    }
}
