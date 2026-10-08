package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity.RoleEntity;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;

@Mapper(uses = RoleIdEmbeddableToRoleIdMapper.class)
public interface RoleEntityToRoleMapper {

    // MapStruct reads the copy method withPermissions as a fluent setter.
    @Mapping(target = "withPermissions", ignore = true)
    Role map(RoleEntity source);
}
