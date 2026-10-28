package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity.UserIdEmbeddable;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

/** The entity key of a user ID, mapped by its {@code value}. */
@Mapper(config = SharedMapperConfig.class)
public interface UserIdToUserIdEmbeddableMapper {

    UserIdEmbeddable map(UserId source);
}
