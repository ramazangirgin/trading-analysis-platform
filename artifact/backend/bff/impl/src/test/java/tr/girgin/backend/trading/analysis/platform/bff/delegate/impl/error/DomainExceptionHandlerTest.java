package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.error.ApiExceptionHandler;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.ApiErrorDto;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error.AnalysisExceptionToApiExceptionMapperImpl;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error.CatalogExceptionToApiExceptionMapperImpl;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error.IdentityExceptionToApiExceptionMapperImpl;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error.SettingsExceptionToApiExceptionMapperImpl;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.exception.CatalogUnavailableException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsError;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsException;

class DomainExceptionHandlerTest {

    private final DomainExceptionHandler handler = new DomainExceptionHandler(
            new ApiExceptionHandler(),
            new AnalysisExceptionToApiExceptionMapperImpl(),
            new CatalogExceptionToApiExceptionMapperImpl(),
            new IdentityExceptionToApiExceptionMapperImpl(),
            new SettingsExceptionToApiExceptionMapperImpl());

    @Test
    void answersAnAnalysisException() {
        ResponseEntity<ApiErrorDto> response = handler.handleAnalysisException(
                new AnalysisException(AnalysisError.NOT_FOUND, "Analysis not found: a1", Map.of("id", "a1")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .isEqualTo(new ApiErrorDto("not_found", "Analysis not found: a1", Map.of("id", "a1")));
    }

    @Test
    void answersAnUnavailableCatalog() {
        ResponseEntity<ApiErrorDto> response = handler.handleCatalogUnavailable(
                new CatalogUnavailableException("runner image missing", new IllegalStateException()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody())
                .isEqualTo(new ApiErrorDto("catalog_unavailable", "runner image missing", Map.of()));
    }

    @Test
    void answersAnIdentityException() {
        ResponseEntity<ApiErrorDto> response = handler.handleIdentityException(
                new IdentityException(IdentityError.USERNAME_TAKEN, "Username taken: ada", Map.of("username", "ada")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody())
                .isEqualTo(new ApiErrorDto("username_taken", "Username taken: ada", Map.of("username", "ada")));
    }

    @Test
    void answersASettingsException() {
        ResponseEntity<ApiErrorDto> response = handler.handleSettingsException(
                new SettingsException(SettingsError.PRESET_NOT_FOUND, "Preset not found: p1", Map.of("id", "p1")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .isEqualTo(new ApiErrorDto("preset_not_found", "Preset not found: p1", Map.of("id", "p1")));
    }
}
