package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;

@Mapper
public interface StringToRoleIdMapper {

    default RoleId map(String source) {
        return new RoleId(source);
    }
}
