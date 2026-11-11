package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.UserDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.UserRoleDto;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserAccount;
import tr.girgin.backend.trading.analysis.platform.library.mapper.OptionalInstantToInstantMapper;
import tr.girgin.backend.trading.analysis.platform.library.mapper.SharedMapperConfig;

@Mapper(config = SharedMapperConfig.class, uses = OptionalInstantToInstantMapper.class)
public interface UserAccountToUserDtoMapper {

    @Mapping(target = "id", source = "id.value")
    @Mapping(target = "username", source = "username.value")
    UserDto map(UserAccount source);

    @Mapping(target = "id", source = "id.value")
    UserRoleDto map(Role source);
}
