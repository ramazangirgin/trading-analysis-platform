package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.row.RoleRow;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;

/** Maps the role's own columns; the adapter adds the permissions with {@link Role#withPermissions}. */
@Mapper(uses = StringToRoleIdMapper.class)
public interface RoleRowToRoleMapper {

    @Mapping(target = "permissions", expression = "java(java.util.Set.of())")
    // MapStruct reads the copy method withPermissions as a fluent setter.
    @Mapping(target = "withPermissions", ignore = true)
    Role map(RoleRow source);
}
