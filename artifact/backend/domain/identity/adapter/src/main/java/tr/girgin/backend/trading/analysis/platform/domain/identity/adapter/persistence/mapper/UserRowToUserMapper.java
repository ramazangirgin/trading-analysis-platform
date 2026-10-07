package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.row.UserRow;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;

/** Maps the user's own columns; the adapter adds the role assignments with {@link User#withRoleIds}. */
@Mapper(
        uses = {
            StringToUserIdMapper.class,
            StringToUsernameMapper.class,
            StringToPasswordHashMapper.class,
            OffsetDateTimeToInstantMapper.class,
            OffsetDateTimeToOptionalInstantMapper.class
        })
public interface UserRowToUserMapper {

    @Mapping(target = "roleIds", expression = "java(java.util.Set.of())")
    // MapStruct reads the copy method withRoleIds as a fluent setter.
    @Mapping(target = "withRoleIds", ignore = true)
    User map(UserRow source);
}
