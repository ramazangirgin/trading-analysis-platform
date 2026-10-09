package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error;

import java.util.Locale;
import org.mapstruct.Mapper;
import org.springframework.http.HttpStatus;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.error.ApiException;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception.SettingsException;

@Mapper
public interface SettingsExceptionToApiExceptionMapper {

    default ApiException map(SettingsException source) {
        HttpStatus status = switch (source.error()) {
            case INVALID_SECRET_NAME, INVALID_SECRET_VALUE, INVALID_PRESET -> HttpStatus.BAD_REQUEST;
            case SECRET_NOT_MANAGED, CONCURRENT_UPDATE -> HttpStatus.CONFLICT;
            case PRESET_NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        return new ApiException(
                status, source.error().name().toLowerCase(Locale.ROOT), source.getMessage(), source.params());
    }
}
