package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.row.RoleRow;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;

@Mapper
public interface RoleToRoleRowMapper {

    @Mapping(target = "id", source = "id.value")
    RoleRow map(Role source);
}
