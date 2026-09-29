package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import org.springframework.http.ResponseEntity;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.SystemHealthDto;

public interface HealthApiDelegate {

    ResponseEntity<SystemHealthDto> getHealth();
}
