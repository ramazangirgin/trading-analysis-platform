package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import java.time.Instant;
import java.util.Optional;
import org.mapstruct.Mapper;

/** An empty {@link Optional} as a null column. */
@Mapper
public interface OptionalInstantToInstantMapper {

    default Instant map(Optional<Instant> source) {
        return source.orElse(null);
    }
}
