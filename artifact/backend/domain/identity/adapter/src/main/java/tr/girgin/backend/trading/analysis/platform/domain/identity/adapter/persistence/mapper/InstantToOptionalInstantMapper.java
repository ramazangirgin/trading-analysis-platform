package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import java.time.Instant;
import java.util.Optional;
import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

/** A nullable column as an {@link Optional}. */
@Mapper(config = SharedMapperConfig.class)
public interface InstantToOptionalInstantMapper {

    default Optional<Instant> map(Instant source) {
        return Optional.ofNullable(source);
    }
}
