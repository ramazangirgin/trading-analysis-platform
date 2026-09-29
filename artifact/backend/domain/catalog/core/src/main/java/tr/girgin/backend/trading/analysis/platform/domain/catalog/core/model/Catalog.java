package tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model;

import java.util.List;
import java.util.Objects;

/** What the installed TradingAgents offers. Read from upstream; the platform keeps no copy. */
public record Catalog(
        String upstreamVersion,
        ModelDefaults defaults,
        List<Provider> providers,
        List<AnalystOption> analysts,
        List<String> assetTypes) {

    public Catalog {
        Objects.requireNonNull(upstreamVersion, "upstreamVersion");
        Objects.requireNonNull(defaults, "defaults");
        providers = List.copyOf(providers);
        analysts = List.copyOf(analysts);
        assetTypes = List.copyOf(assetTypes);
    }
}
