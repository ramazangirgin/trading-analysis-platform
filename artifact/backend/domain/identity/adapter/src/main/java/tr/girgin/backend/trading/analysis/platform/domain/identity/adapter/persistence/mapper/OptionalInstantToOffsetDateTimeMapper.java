package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.mapstruct.Mapper;

/** An empty {@link Optional} as a null column, a present one in UTC. */
@Mapper
public interface OptionalInstantToOffsetDateTimeMapper {

    default OffsetDateTime map(Optional<Instant> source) {
        return source.map(instant -> instant.atOffset(ZoneOffset.UTC)).orElse(null);
    }
}
