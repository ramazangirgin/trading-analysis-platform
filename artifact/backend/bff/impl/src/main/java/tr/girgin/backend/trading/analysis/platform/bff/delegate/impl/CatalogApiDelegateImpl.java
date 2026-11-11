package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.CatalogApiDelegate;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.CatalogDto;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.RequiresPermission;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.CatalogToCatalogDtoMapper;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.inbound.GetCatalogPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;

@Service
class CatalogApiDelegateImpl implements CatalogApiDelegate {

    private final GetCatalogPort getCatalogPort;
    private final CatalogToCatalogDtoMapper catalogToCatalogDtoMapper;

    CatalogApiDelegateImpl(GetCatalogPort getCatalogPort, CatalogToCatalogDtoMapper catalogToCatalogDtoMapper) {
        this.getCatalogPort = getCatalogPort;
        this.catalogToCatalogDtoMapper = catalogToCatalogDtoMapper;
    }

    @Override
    // The new-analysis form and the key table of the settings page read the catalog.
    @RequiresPermission({Permission.ANALYSIS_RUN, Permission.SETTINGS_READ})
    public CatalogDto getCatalog() {
        return catalogToCatalogDtoMapper.map(getCatalogPort.getCatalog());
    }
}
