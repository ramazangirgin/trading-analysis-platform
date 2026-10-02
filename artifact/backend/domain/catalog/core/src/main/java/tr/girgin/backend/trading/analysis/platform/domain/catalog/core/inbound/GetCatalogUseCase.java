package tr.girgin.backend.trading.analysis.platform.domain.catalog.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Catalog;

/** What a run can be configured with: providers, models and analysts, as the engine reports them. */
public interface GetCatalogUseCase {

    Catalog getCatalog();
}
