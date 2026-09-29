package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SystemHealthDto;

/** Diagnostics for the UI: runner installed, provider keys, data dir, queue. */
@RestController
@RequestMapping("/api/health")
public class HealthApiController {

    private final HealthApiDelegate delegate;

    public HealthApiController(HealthApiDelegate delegate) {
        this.delegate = delegate;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SystemHealthDto> getHealth() {
        return delegate.getHealth();
    }
}
