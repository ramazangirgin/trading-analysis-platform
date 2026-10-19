package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.error.ApiException;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.exception.CatalogUnavailableException;

class CatalogExceptionToApiExceptionMapperTest {

    private final CatalogExceptionToApiExceptionMapper mapper = new CatalogExceptionToApiExceptionMapperImpl();

    @Test
    void mapsAnUnavailableCatalogToServiceUnavailable() {
        ApiException mapped =
                mapper.map(new CatalogUnavailableException("runner image missing", new IllegalStateException()));

        assertThat(mapped.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(mapped.errorCode()).isEqualTo("catalog_unavailable");
        assertThat(mapped.getMessage()).isEqualTo("runner image missing");
    }
}
