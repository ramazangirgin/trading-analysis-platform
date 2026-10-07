package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import java.time.Instant;
import org.mapstruct.Mapper;

@Mapper
public interface StringToInstantMapper {

    default Instant map(String source) {
        return source == null ? null : Instant.parse(source);
    }
}
