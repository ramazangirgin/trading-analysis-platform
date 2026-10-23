package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error;

import java.util.Locale;
import org.mapstruct.Mapper;
import org.springframework.http.HttpStatus;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.error.ApiException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;

@Mapper
public interface AnalysisExceptionToApiExceptionMapper {

    default ApiException map(AnalysisException source) {
        HttpStatus status = switch (source.error()) {
            case INVALID_SPEC -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ALREADY_RUNNING, NOT_RUNNING, CONCURRENT_UPDATE, NOT_SHAREABLE -> HttpStatus.CONFLICT;
            case OVERRIDE_FORBIDDEN, SHARE_FORBIDDEN -> HttpStatus.FORBIDDEN;
        };
        String code = source.error().name().toLowerCase(Locale.ROOT);
        if (source.error() == AnalysisError.OVERRIDE_FORBIDDEN
                || source.error() == AnalysisError.SHARE_FORBIDDEN
                || source.error() == AnalysisError.NOT_SHAREABLE) {
            // "forbidden" alone is the code of a missing endpoint permission
            code = "analysis_" + code;
        }
        return new ApiException(status, code, source.getMessage(), source.params());
    }
}
