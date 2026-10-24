package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisPreviewDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisPreviewPageDto;

public interface PublicAnalysesApiDelegate {

    AnalysisPreviewPageDto listPublicAnalyses(int page, int size);

    AnalysisPreviewDto getPublicAnalysis(String id);
}
