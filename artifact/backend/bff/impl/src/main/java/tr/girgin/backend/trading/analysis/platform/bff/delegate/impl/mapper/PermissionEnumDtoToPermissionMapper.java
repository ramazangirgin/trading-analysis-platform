package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PermissionEnumDto;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;

@Mapper
public interface PermissionEnumDtoToPermissionMapper {

    Permission map(PermissionEnumDto source);
}
