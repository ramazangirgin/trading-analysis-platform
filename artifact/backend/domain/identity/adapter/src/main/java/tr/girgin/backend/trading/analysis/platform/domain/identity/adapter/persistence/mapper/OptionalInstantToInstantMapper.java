package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import java.time.Instant;
import java.util.Optional;
import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

/** An empty {@link Optional} as a null column. */
@Mapper(config = SharedMapperConfig.class)
public interface OptionalInstantToInstantMapper {

    default Instant map(Optional<Instant> source) {
        return source.orElse(null);
    }
}
