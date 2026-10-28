package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity.UserIdEmbeddable;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

/** A user ID from its entity key, mapped by its {@code value}. */
@Mapper(config = SharedMapperConfig.class)
public interface UserIdEmbeddableToUserIdMapper {

    UserId map(UserIdEmbeddable source);
}
