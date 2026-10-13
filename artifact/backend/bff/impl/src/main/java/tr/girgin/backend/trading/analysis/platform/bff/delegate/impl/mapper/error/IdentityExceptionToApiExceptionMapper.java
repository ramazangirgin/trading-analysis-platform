package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error;

import java.util.Locale;
import org.mapstruct.Mapper;
import org.springframework.http.HttpStatus;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.error.ApiException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;

@Mapper
public interface IdentityExceptionToApiExceptionMapper {

    default ApiException map(IdentityException source) {
        // INVALID_USERNAME and UNKNOWN_PERMISSION cannot come out of a login: the use case turns an
        // invalid username into BAD_CREDENTIALS. Reaching them is a bug, so 500.
        HttpStatus status = switch (source.error()) {
            case BAD_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case WRONG_CURRENT_PASSWORD, PASSWORD_UNCHANGED, WEAK_PASSWORD -> HttpStatus.BAD_REQUEST;
            case INVALID_USERNAME, UNKNOWN_PERMISSION -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return new ApiException(
                status, source.error().name().toLowerCase(Locale.ROOT), source.getMessage(), source.params());
    }
}
