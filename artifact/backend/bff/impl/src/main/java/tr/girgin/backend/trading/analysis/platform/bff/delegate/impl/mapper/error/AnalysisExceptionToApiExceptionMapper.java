package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error;

import java.util.Locale;
import org.mapstruct.Mapper;
import org.springframework.http.HttpStatus;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.error.ApiException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;

@Mapper
public interface AnalysisExceptionToApiExceptionMapper {

    default ApiException map(AnalysisException source) {
        HttpStatus status = switch (source.error()) {
            case INVALID_SPEC -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ALREADY_RUNNING, NOT_RUNNING -> HttpStatus.CONFLICT;
        };
        return new ApiException(status, source.error().name().toLowerCase(Locale.ROOT), source.getMessage(),
                source.params());
    }
}
