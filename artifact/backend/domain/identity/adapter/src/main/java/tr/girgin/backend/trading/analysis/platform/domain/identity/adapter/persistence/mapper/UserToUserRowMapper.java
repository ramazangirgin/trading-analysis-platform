package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.row.UserRow;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;

@Mapper(uses = {InstantToStringMapper.class, OptionalInstantToStringMapper.class})
public interface UserToUserRowMapper {

    @Mapping(target = "id", source = "id.value")
    @Mapping(target = "username", source = "username.value")
    @Mapping(target = "passwordHash", source = "passwordHash.value")
    UserRow map(User source);
}
