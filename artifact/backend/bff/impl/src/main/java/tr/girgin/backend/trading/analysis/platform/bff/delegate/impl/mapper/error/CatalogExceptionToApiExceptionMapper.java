package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error;

import java.util.Map;
import org.mapstruct.Mapper;
import org.springframework.http.HttpStatus;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.error.ApiException;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.exception.CatalogUnavailableException;

@Mapper
public interface CatalogExceptionToApiExceptionMapper {

    default ApiException map(CatalogUnavailableException source) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "catalog_unavailable", source.getMessage(), Map.of());
    }
}
