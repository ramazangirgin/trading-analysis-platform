package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import org.springframework.http.ResponseEntity;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.ImportResultDto;

public interface ReportsApiDelegate {

    ResponseEntity<ImportResultDto> rescan();
}
