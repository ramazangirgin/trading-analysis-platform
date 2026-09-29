package tr.girgin.backend.trading.analysis.platform.domain.catalog.core.service;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.inbound.GetCatalogUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.inbound.GetEngineVersionUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Catalog;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.EngineVersion;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.outbound.runner.EngineInfoPort;

/**
 * Caches what the runner reports: asking it starts a Python process that imports TradingAgents,
 * which takes seconds. Failures are not cached, so a fixed installation is picked up at once.
 */
@Service
class CatalogService implements GetCatalogUseCase, GetEngineVersionUseCase {

    private final EngineInfoPort engine;
    private final Duration ttl;
    private final Cached<Catalog> catalog = new Cached<>();
    private final Cached<EngineVersion> version = new Cached<>();

    CatalogService(EngineInfoPort engine, @Value("${platform.catalog.cache-minutes:10}") long cacheMinutes) {
        this.engine = engine;
        this.ttl = Duration.ofMinutes(cacheMinutes);
    }

    @Override
    public Catalog getCatalog() {
        return catalog.get(engine::fetchCatalog, ttl);
    }

    @Override
    public EngineVersion getEngineVersion() {
        return version.get(engine::fetchVersion, ttl);
    }

    private static final class Cached<T> {

        private T value;
        private Instant fetchedAt = Instant.MIN;

        synchronized T get(Supplier<T> fetch, Duration ttl) {
            if (value == null || !Instant.now().isBefore(fetchedAt.plus(ttl))) {
                value = fetch.get();
                fetchedAt = Instant.now();
            }
            return value;
        }
    }
}
