package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;

@Mapper
public interface StringToPermissionMapper {

    default Permission map(String source) {
        return Permission.fromKey(source);
    }
}
