package tr.girgin.backend.trading.analysis.platform.domain.catalog.core.outbound.runner;

import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Catalog;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.EngineVersion;

/** Asks the installed runner what it offers. Slow (it loads TradingAgents): callers cache. */
public interface EngineInfoPort {

    Catalog fetchCatalog();

    EngineVersion fetchVersion();
}
