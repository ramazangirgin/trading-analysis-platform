package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity.UserEntity;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;

@Mapper(uses = {UserIdToUserIdEmbeddableMapper.class, OptionalInstantToInstantMapper.class})
public interface UserToUserEntityMapper {

    UserEntity map(User source);
}
