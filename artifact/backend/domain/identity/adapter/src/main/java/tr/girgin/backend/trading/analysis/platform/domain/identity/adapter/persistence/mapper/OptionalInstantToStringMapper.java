package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import java.time.Instant;
import java.util.Optional;
import org.mapstruct.Mapper;

/** An empty {@link Optional} as a null column, a present one in the format of {@link InstantToStringMapper}. */
@Mapper
public interface OptionalInstantToStringMapper {

    default String map(Optional<Instant> source) {
        return source.map(InstantToStringMapper.FORMAT::format).orElse(null);
    }
}
