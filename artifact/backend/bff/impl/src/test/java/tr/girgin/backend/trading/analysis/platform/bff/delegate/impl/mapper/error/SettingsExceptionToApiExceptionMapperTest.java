package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.error.ApiException;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsError;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsException;

class SettingsExceptionToApiExceptionMapperTest {

    private final SettingsExceptionToApiExceptionMapper mapper = new SettingsExceptionToApiExceptionMapperImpl();

    @Test
    void mapsAConcurrentUpdateToConflict() {
        ApiException mapped =
                mapper.map(new SettingsException(SettingsError.CONCURRENT_UPDATE, "changed", Map.of("id", "p1")));

        assertThat(mapped.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mapped.errorCode()).isEqualTo("concurrent_update");
        assertThat(mapped.params()).containsEntry("id", "p1");
    }
}
