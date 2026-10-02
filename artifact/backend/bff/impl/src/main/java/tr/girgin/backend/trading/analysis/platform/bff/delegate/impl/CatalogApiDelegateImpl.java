package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.CatalogApiDelegate;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.error.ApiException;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.CatalogDto;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.CatalogToCatalogDtoMapper;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.exception.CatalogUnavailableException;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.inbound.GetCatalogUseCase;

@Service
class CatalogApiDelegateImpl implements CatalogApiDelegate {

    private final GetCatalogUseCase getCatalog;
    private final CatalogToCatalogDtoMapper mapper;

    CatalogApiDelegateImpl(GetCatalogUseCase getCatalog, CatalogToCatalogDtoMapper mapper) {
        this.getCatalog = getCatalog;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<CatalogDto> getCatalog() {
        try {
            return ResponseEntity.ok(mapper.map(getCatalog.getCatalog()));
        } catch (CatalogUnavailableException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "catalog_unavailable", e.getMessage(), Map.of());
        }
    }
}
