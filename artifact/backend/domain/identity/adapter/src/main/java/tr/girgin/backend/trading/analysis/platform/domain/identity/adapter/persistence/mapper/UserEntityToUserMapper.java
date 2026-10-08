package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity.UserEntity;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;

@Mapper(uses = {UserIdEmbeddableToUserIdMapper.class, InstantToOptionalInstantMapper.class})
public interface UserEntityToUserMapper {

    // MapStruct reads the copy method withRoleIds as a fluent setter.
    @Mapping(target = "withRoleIds", ignore = true)
    User map(UserEntity source);
}
