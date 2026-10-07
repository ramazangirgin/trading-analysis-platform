package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.mapstruct.Mapper;

/** A nullable column as an {@link Optional}. */
@Mapper
public interface OffsetDateTimeToOptionalInstantMapper {

    default Optional<Instant> map(OffsetDateTime source) {
        return Optional.ofNullable(source).map(OffsetDateTime::toInstant);
    }
}
