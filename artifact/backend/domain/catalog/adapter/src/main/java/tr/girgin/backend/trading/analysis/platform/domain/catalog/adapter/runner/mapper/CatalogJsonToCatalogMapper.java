package tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.json.CatalogJson;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Catalog;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.ModelDefaults;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Provider;

@Mapper
public interface CatalogJsonToCatalogMapper {

    @Mapping(target = "providers", defaultExpression = "java(java.util.List.of())")
    @Mapping(target = "analysts", defaultExpression = "java(java.util.List.of())")
    @Mapping(target = "assetTypes", defaultExpression = "java(java.util.List.of())")
    Catalog map(CatalogJson source);

    ModelDefaults map(CatalogJson.DefaultsJson source);

    @Mapping(target = "customModelAllowed", defaultValue = "false")
    @Mapping(target = "quickModels", defaultExpression = "java(java.util.List.of())")
    @Mapping(target = "deepModels", defaultExpression = "java(java.util.List.of())")
    Provider map(CatalogJson.ProviderJson source);
}
