package tr.girgin.backend.trading.analysis.platform.domain.catalog.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.EngineVersion;

/** The version of the analysis engine (ta-runner and upstream TradingAgents) the platform runs. */
public interface GetEngineVersionUseCase {

    EngineVersion getEngineVersion();
}
