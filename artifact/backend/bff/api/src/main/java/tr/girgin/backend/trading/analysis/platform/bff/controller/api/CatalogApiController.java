package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.CatalogDto;

/** Providers, models and analysts the installed TradingAgents offers. */
@RestController
@RequestMapping("/api/catalog")
public class CatalogApiController {

    private final CatalogApiDelegate delegate;

    public CatalogApiController(CatalogApiDelegate delegate) {
        this.delegate = delegate;
    }

    @GetMapping
    public ResponseEntity<CatalogDto> getCatalog() {
        return delegate.getCatalog();
    }
}
