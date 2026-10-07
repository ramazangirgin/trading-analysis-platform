package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.mapstruct.Mapper;

/** Fixed-width UTC text, so that ordering by the column is ordering by time. */
@Mapper
public interface InstantToStringMapper {

    DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX").withZone(ZoneOffset.UTC);

    default String map(Instant source) {
        return source == null ? null : FORMAT.format(source);
    }
}
