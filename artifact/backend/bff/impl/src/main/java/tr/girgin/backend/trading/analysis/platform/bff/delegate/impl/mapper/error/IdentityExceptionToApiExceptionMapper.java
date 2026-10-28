package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error;

import java.util.Locale;
import org.mapstruct.Mapper;
import org.springframework.http.HttpStatus;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.error.ApiException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

@Mapper(config = SharedMapperConfig.class)
public interface IdentityExceptionToApiExceptionMapper {

    default ApiException map(IdentityException source) {
        // UNKNOWN_PERMISSION cannot come out of any request: reaching it is a bug, so 500. INVALID_USERNAME comes
        // from user input when an admin creates a user (a login turns it into BAD_CREDENTIALS), so 400.
        HttpStatus status = switch (source.error()) {
            case BAD_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case WRONG_CURRENT_PASSWORD,
                    PASSWORD_UNCHANGED,
                    WEAK_PASSWORD,
                    INVALID_USERNAME,
                    UNKNOWN_ROLE,
                    INVALID_ROLE_NAME,
                    INVALID_ROLE_DESCRIPTION -> HttpStatus.BAD_REQUEST;
            case USER_NOT_FOUND, ROLE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case USERNAME_TAKEN,
                    CANNOT_DISABLE_SELF,
                    CANNOT_REMOVE_OWN_ADMIN,
                    LAST_ADMIN,
                    CONCURRENT_UPDATE,
                    ROLE_NAME_TAKEN,
                    BUILT_IN_ROLE,
                    ROLE_IN_USE -> HttpStatus.CONFLICT;
            case UNKNOWN_PERMISSION -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return new ApiException(
                status, source.error().name().toLowerCase(Locale.ROOT), source.getMessage(), source.params());
    }
}
