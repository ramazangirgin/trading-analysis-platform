package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;

/** The instant in UTC, as the PostgreSQL driver writes a {@code timestamptz} column. */
@Mapper
public interface InstantToOffsetDateTimeMapper {

    default OffsetDateTime map(Instant source) {
        return source == null ? null : source.atOffset(ZoneOffset.UTC);
    }
}
