package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisReportDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.AnalysisStatusDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.StartAnalysisRequest;

@RestController
@RequestMapping("/api/analyses")
public class AnalysesApiController {

    private final AnalysesApiDelegate delegate;

    public AnalysesApiController(AnalysesApiDelegate delegate) {
        this.delegate = delegate;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AnalysisDto> startAnalysis(@Valid @RequestBody StartAnalysisRequest request) {
        return delegate.startAnalysis(request);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<AnalysisDto>> listAnalyses(
            @RequestParam(required = false) AnalysisStatusDto status,
            @RequestParam(required = false) String ticker) {
        return delegate.listAnalyses(status, ticker);
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AnalysisDto> getAnalysis(@PathVariable String id) {
        return delegate.getAnalysis(id);
    }

    @PostMapping(path = "/{id}/stop", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AnalysisDto> stopAnalysis(@PathVariable String id) {
        return delegate.stopAnalysis(id);
    }

    @PostMapping(path = "/{id}/rerun", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AnalysisDto> rerunAnalysis(@PathVariable String id) {
        return delegate.rerunAnalysis(id);
    }

    /** The report files in the data dir for this analysis' ticker and date. */
    @GetMapping(path = "/{id}/report", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AnalysisReportDto> getReport(@PathVariable String id) {
        return delegate.getReport(id);
    }

    /**
     * Server-sent events: the run's events from {@code Last-Event-ID} (or {@code after}) onwards,
     * each with its {@code seq} as the event id, then an {@code end} event once the run is over.
     */
    @GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(
            @PathVariable String id,
            @RequestHeader(name = "Last-Event-ID", required = false) Long lastEventId,
            @RequestParam(name = "after", required = false) Long after) {
        long cursor = lastEventId != null ? lastEventId : after != null ? after : 0L;
        return delegate.streamEvents(id, Math.max(cursor, 0L));
    }
}
