package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity.RoleEntity;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;

@Mapper(uses = RoleIdToRoleIdEmbeddableMapper.class)
public interface RoleToRoleEntityMapper {

    RoleEntity map(Role source);
}
