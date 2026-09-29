package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import java.util.Map;
import org.mapstruct.Mapper;
import org.springframework.http.HttpStatus;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.ApiException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

/** An id that cannot exist is reported like one that does not. */
@Mapper
interface StringToAnalysisIdMapper {

    default AnalysisId map(String source) {
        try {
            return new AnalysisId(source);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Analysis not found: " + source,
                    Map.of("id", source));
        }
    }
}
