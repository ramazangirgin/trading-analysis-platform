package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import java.util.Map;
import org.mapstruct.Mapper;
import org.springframework.http.HttpStatus;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.error.ApiException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

/** An id that cannot exist is reported like one that does not. */
@Mapper
public interface StringToAnalysisIdMapper {

    default AnalysisId map(String source) {
        try {
            return new AnalysisId(source);
        } catch (IllegalArgumentException _) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, "not_found", "Analysis not found: " + source, Map.of("id", source));
        }
    }
}
