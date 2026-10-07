package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import org.mapstruct.Mapper;

@Mapper
public interface OffsetDateTimeToInstantMapper {

    default Instant map(OffsetDateTime source) {
        return source == null ? null : source.toInstant();
    }
}
