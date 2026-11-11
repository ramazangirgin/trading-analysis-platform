package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.PublicAnalysesApiDelegate;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisPreviewDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisPreviewPageDto;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.NoPermissionRequired;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.AnalysisPreviewSummaryToAnalysisPreviewSummaryDtoMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.AnalysisPreviewToAnalysisPreviewDtoMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.orchestration.preview.inbound.GetAnalysisPreviewUseCase;
import tr.girgin.backend.trading.analysis.platform.orchestration.preview.inbound.ListAnalysisPreviewsUseCase;
import tr.girgin.backend.trading.analysis.platform.orchestration.preview.model.AnalysisPreviewPage;

@Service
class PublicAnalysesApiDelegateImpl implements PublicAnalysesApiDelegate {

    private final ListAnalysisPreviewsUseCase listAnalysisPreviewsUseCase;
    private final GetAnalysisPreviewUseCase getAnalysisPreviewUseCase;
    private final AnalysisPreviewSummaryToAnalysisPreviewSummaryDtoMapper
            analysisPreviewSummaryToAnalysisPreviewSummaryDtoMapper;
    private final AnalysisPreviewToAnalysisPreviewDtoMapper analysisPreviewToAnalysisPreviewDtoMapper;

    PublicAnalysesApiDelegateImpl(
            ListAnalysisPreviewsUseCase listAnalysisPreviewsUseCase,
            GetAnalysisPreviewUseCase getAnalysisPreviewUseCase,
            AnalysisPreviewSummaryToAnalysisPreviewSummaryDtoMapper
                    analysisPreviewSummaryToAnalysisPreviewSummaryDtoMapper,
            AnalysisPreviewToAnalysisPreviewDtoMapper analysisPreviewToAnalysisPreviewDtoMapper) {
        this.listAnalysisPreviewsUseCase = listAnalysisPreviewsUseCase;
        this.getAnalysisPreviewUseCase = getAnalysisPreviewUseCase;
        this.analysisPreviewSummaryToAnalysisPreviewSummaryDtoMapper =
                analysisPreviewSummaryToAnalysisPreviewSummaryDtoMapper;
        this.analysisPreviewToAnalysisPreviewDtoMapper = analysisPreviewToAnalysisPreviewDtoMapper;
    }

    @Override
    @NoPermissionRequired("open to visitors: the preview of the partial analyses, cut on the server")
    public AnalysisPreviewPageDto listPublicAnalyses(int page, int size) {
        AnalysisPreviewPage previews = listAnalysisPreviewsUseCase.list(page, size);
        return new AnalysisPreviewPageDto(
                previews.items().stream()
                        .map(analysisPreviewSummaryToAnalysisPreviewSummaryDtoMapper::map)
                        .toList(),
                previews.hasMore());
    }

    @Override
    @NoPermissionRequired("open to visitors: the preview of one partial analysis, cut on the server")
    public AnalysisPreviewDto getPublicAnalysis(String id) {
        return analysisPreviewToAnalysisPreviewDtoMapper.map(getAnalysisPreviewUseCase.get(new AnalysisId(id)));
    }
}
