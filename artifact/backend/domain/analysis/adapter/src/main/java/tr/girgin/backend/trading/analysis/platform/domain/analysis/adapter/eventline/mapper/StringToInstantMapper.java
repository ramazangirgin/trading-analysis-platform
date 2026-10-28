package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.eventline.mapper;

import java.time.Instant;
import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

@Mapper(config = SharedMapperConfig.class)
public interface StringToInstantMapper {

    default Instant map(String source) {
        return source == null ? null : Instant.parse(source);
    }
}
