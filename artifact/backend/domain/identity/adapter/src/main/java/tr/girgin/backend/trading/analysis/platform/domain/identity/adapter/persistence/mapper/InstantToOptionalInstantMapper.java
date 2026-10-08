package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import java.time.Instant;
import java.util.Optional;
import org.mapstruct.Mapper;

/** A nullable column as an {@link Optional}. */
@Mapper
public interface InstantToOptionalInstantMapper {

    default Optional<Instant> map(Instant source) {
        return Optional.ofNullable(source);
    }
}
