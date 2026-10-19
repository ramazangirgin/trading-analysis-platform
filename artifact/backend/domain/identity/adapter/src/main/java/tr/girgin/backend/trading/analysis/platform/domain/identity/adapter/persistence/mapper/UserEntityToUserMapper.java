package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity.UserEntity;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;

@Mapper(uses = {UserIdEmbeddableToUserIdMapper.class, InstantToOptionalInstantMapper.class})
public interface UserEntityToUserMapper {

    // MapStruct reads the copy methods withRoleIds, withEnabled and withLastLogin as fluent setters.
    @Mapping(target = "withRoleIds", ignore = true)
    @Mapping(target = "withEnabled", ignore = true)
    @Mapping(target = "withLastLogin", ignore = true)
    // The column comes with the identity adapter package; until then no user has logged in.
    @Mapping(target = "lastLoginAt", expression = "java(java.util.Optional.empty())")
    User map(UserEntity source);
}
