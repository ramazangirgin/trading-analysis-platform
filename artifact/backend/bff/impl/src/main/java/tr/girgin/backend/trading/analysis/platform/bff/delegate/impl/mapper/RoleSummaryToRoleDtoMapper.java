package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PermissionDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RoleDto;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.RoleSummary;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;

@Mapper(uses = PermissionToPermissionDtoMapper.class)
public interface RoleSummaryToRoleDtoMapper {

    @Mapping(target = "id", source = "role.id.value")
    @Mapping(target = "name", source = "role.name")
    @Mapping(target = "builtIn", source = "role.builtIn")
    @Mapping(target = "description", source = "role.description")
    @Mapping(target = "permissions", expression = "java(map(map(source.role().permissions())))")
    @Mapping(target = "version", source = "role.version")
    RoleDto map(RoleSummary source);

    List<PermissionDto> map(List<Permission> source);

    default List<Permission> map(Set<Permission> source) {
        return source.stream().sorted(Comparator.comparing(Permission::key)).toList();
    }
}
