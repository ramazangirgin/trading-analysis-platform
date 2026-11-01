package tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model;

import java.util.List;

/** What the installed TradingAgents offers. Read from upstream; the platform keeps no copy. */
public record Catalog(
        String upstreamVersion,
        ModelDefaults defaults,
        List<Provider> providers,
        List<AnalystOption> analysts,
        List<String> assetTypes) {}
