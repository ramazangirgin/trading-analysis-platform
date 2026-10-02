package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.CatalogDto;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Catalog;

@Mapper
public interface CatalogToCatalogDtoMapper {

    CatalogDto map(Catalog source);
}
