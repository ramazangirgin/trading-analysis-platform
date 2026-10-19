package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisReportDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisStatusDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PriceHistoryDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RunLogDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.StartAnalysisRequest;

public interface AnalysesApiDelegate {

    ResponseEntity<AnalysisDto> startAnalysis(StartAnalysisRequest request);

    List<AnalysisDto> listAnalyses(AnalysisStatusDto status, String ticker, boolean allUsers);

    AnalysisDto getAnalysis(String id);

    AnalysisDto stopAnalysis(String id);

    ResponseEntity<AnalysisDto> rerunAnalysis(String id);

    AnalysisReportDto getReport(String id);

    PriceHistoryDto getPrices(String id);

    RunLogDto getLogs(String id, int tail);

    SseEmitter streamEvents(String id, long afterSeq, HttpServletRequest http);
}
