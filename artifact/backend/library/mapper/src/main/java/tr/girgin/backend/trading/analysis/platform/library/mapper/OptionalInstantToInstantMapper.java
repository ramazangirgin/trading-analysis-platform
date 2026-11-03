package tr.girgin.backend.trading.analysis.platform.library.mapper;

import java.time.Instant;
import java.util.Optional;
import org.mapstruct.Mapper;

/** An empty {@link Optional} as null. */
@Mapper(config = SharedMapperConfig.class)
public interface OptionalInstantToInstantMapper {

    default Instant map(Optional<Instant> source) {
        return source.orElse(null);
    }
}
