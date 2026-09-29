package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.CatalogDto;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Catalog;

@Mapper
interface CatalogToCatalogDtoMapper {

    CatalogDto map(Catalog source);
}
