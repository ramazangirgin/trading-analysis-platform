package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import java.time.Instant;
import org.mapstruct.Mapper;

@Mapper
interface StringToTimestampMapper {

    default Instant map(String source) {
        return source == null ? null : Instant.parse(source);
    }
}
