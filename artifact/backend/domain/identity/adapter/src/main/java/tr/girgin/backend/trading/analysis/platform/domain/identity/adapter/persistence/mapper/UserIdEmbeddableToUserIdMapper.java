package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity.UserIdEmbeddable;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;

/** A user ID from its entity key, mapped by its {@code value}. */
@Mapper
public interface UserIdEmbeddableToUserIdMapper {

    UserId map(UserIdEmbeddable source);
}
