package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisStatusDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.StartAnalysisRequest;

public interface AnalysesApiDelegate {

    ResponseEntity<AnalysisDto> startAnalysis(StartAnalysisRequest request);

    ResponseEntity<List<AnalysisDto>> listAnalyses(AnalysisStatusDto status, String ticker);

    ResponseEntity<AnalysisDto> getAnalysis(String id);

    ResponseEntity<AnalysisDto> stopAnalysis(String id);

    ResponseEntity<AnalysisDto> rerunAnalysis(String id);

    SseEmitter streamEvents(String id, long afterSeq);
}
