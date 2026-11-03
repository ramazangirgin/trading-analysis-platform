package tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.json.CatalogJson;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Catalog;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.ModelDefaults;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.Provider;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

@Mapper(config = SharedMapperConfig.class, nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface CatalogJsonToCatalogMapper {

    Catalog map(CatalogJson source);

    List<String> map(List<String> source);

    ModelDefaults map(CatalogJson.DefaultsJson source);

    @Mapping(target = "customModelAllowed", defaultValue = "false")
    Provider map(CatalogJson.ProviderJson source);
}
