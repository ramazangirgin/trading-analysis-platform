package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.ImportResultDto;

@RestController
@RequestMapping("/api/reports")
public class ReportsApiController {

    private final ReportsApiDelegate delegate;

    public ReportsApiController(ReportsApiDelegate delegate) {
        this.delegate = delegate;
    }

    /** Scans the data dir again and imports runs the platform did not start. */
    @PostMapping(path = "/rescan", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ImportResultDto> rescan() {
        return delegate.rescan();
    }
}
