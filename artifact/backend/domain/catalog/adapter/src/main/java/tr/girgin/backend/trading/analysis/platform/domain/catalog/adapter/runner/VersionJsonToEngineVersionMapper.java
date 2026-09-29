package tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model.EngineVersion;

@Mapper
interface VersionJsonToEngineVersionMapper {

    @Mapping(target = "protocolVersion", defaultValue = "0")
    EngineVersion map(VersionJson source);
}
