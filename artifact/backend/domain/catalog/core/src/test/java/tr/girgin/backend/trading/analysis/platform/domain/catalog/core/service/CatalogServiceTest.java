package tr.girgin.backend.trading.analysis.platform.domain.catalog.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Catalog;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.CatalogUnavailableException;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.EngineVersion;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.ModelDefaults;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.outbound.runner.EngineInfoPort;

class CatalogServiceTest {

    private final AtomicInteger calls = new AtomicInteger();
    private boolean failing;

    private final EngineInfoPort engine = new EngineInfoPort() {
        @Override
        public Catalog fetchCatalog() {
            calls.incrementAndGet();
            if (failing) {
                throw new CatalogUnavailableException("runner missing", null);
            }
            return new Catalog("0.5.1", new ModelDefaults("openai", "a", "b"), List.of(), List.of(), List.of("stock"));
        }

        @Override
        public EngineVersion fetchVersion() {
            calls.incrementAndGet();
            return new EngineVersion("0.1.0", 1, "0.5.1");
        }
    };

    @Test
    void asksTheRunnerOnceWithinTheCacheTime() {
        CatalogService service = new CatalogService(engine, 10);

        service.getCatalog();
        service.getCatalog();

        assertThat(calls).hasValue(1);
    }

    @Test
    void doesNotCacheFailures() {
        CatalogService service = new CatalogService(engine, 10);
        failing = true;
        assertThatThrownBy(service::getCatalog).isInstanceOf(CatalogUnavailableException.class);

        failing = false;

        assertThat(service.getCatalog().upstreamVersion()).isEqualTo("0.5.1");
        assertThat(calls).hasValue(2);
    }

    @Test
    void cachesTheVersionSeparately() {
        CatalogService service = new CatalogService(engine, 0);

        assertThat(service.getEngineVersion().protocolVersion()).isEqualTo(1);
        service.getEngineVersion();

        assertThat(calls).hasValue(2);
    }
}
