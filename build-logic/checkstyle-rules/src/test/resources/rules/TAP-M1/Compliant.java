package tr.girgin.backend.trading.analysis.platform.demo;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

@Mapper(config = SharedMapperConfig.class)
interface SharedMapper {
    String map(Integer value);
}

@Mapper(config = SharedMapperConfig.class, uses = SharedMapper.class)
interface SharedWithUsesMapper {
    String map(Integer value);
}

@com.example.other.Mapper
interface OtherLibraryMapper {
    String map(Integer value);
}
