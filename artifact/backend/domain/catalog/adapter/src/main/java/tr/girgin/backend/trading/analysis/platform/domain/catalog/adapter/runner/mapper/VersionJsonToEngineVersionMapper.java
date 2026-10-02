package tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.json.VersionJson;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.EngineVersion;

@Mapper
public interface VersionJsonToEngineVersionMapper {

    @Mapping(target = "protocolVersion", defaultValue = "0")
    EngineVersion map(VersionJson source);
}
