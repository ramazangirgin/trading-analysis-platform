package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.error;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.error.ApiExceptionHandler;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.ApiErrorDto;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error.AnalysisExceptionToApiExceptionMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error.CatalogExceptionToApiExceptionMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error.IdentityExceptionToApiExceptionMapper;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error.SettingsExceptionToApiExceptionMapper;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.exception.CatalogUnavailableException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsException;

// The bff api module cannot see the domain, so the domain's exceptions are answered here.
@RestControllerAdvice
public class DomainExceptionHandler {

    private final ApiExceptionHandler apiErrors;
    private final AnalysisExceptionToApiExceptionMapper analysisErrors;
    private final CatalogExceptionToApiExceptionMapper catalogErrors;
    private final IdentityExceptionToApiExceptionMapper identityErrors;
    private final SettingsExceptionToApiExceptionMapper settingsErrors;

    DomainExceptionHandler(
            ApiExceptionHandler apiErrors,
            AnalysisExceptionToApiExceptionMapper analysisErrors,
            CatalogExceptionToApiExceptionMapper catalogErrors,
            IdentityExceptionToApiExceptionMapper identityErrors,
            SettingsExceptionToApiExceptionMapper settingsErrors) {
        this.apiErrors = apiErrors;
        this.analysisErrors = analysisErrors;
        this.catalogErrors = catalogErrors;
        this.identityErrors = identityErrors;
        this.settingsErrors = settingsErrors;
    }

    @ExceptionHandler(AnalysisException.class)
    public ResponseEntity<ApiErrorDto> handleAnalysisException(AnalysisException ex) {
        return apiErrors.response(analysisErrors.map(ex));
    }

    @ExceptionHandler(CatalogUnavailableException.class)
    public ResponseEntity<ApiErrorDto> handleCatalogUnavailable(CatalogUnavailableException ex) {
        return apiErrors.response(catalogErrors.map(ex));
    }

    @ExceptionHandler(IdentityException.class)
    public ResponseEntity<ApiErrorDto> handleIdentityException(IdentityException ex) {
        return apiErrors.response(identityErrors.map(ex));
    }

    @ExceptionHandler(SettingsException.class)
    public ResponseEntity<ApiErrorDto> handleSettingsException(SettingsException ex) {
        return apiErrors.response(settingsErrors.map(ex));
    }
}
