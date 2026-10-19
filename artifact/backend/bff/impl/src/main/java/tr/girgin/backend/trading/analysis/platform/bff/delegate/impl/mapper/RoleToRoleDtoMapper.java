package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.PermissionDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.RoleDto;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;

/** The permissions are sorted by key. */
@Mapper(uses = PermissionToPermissionDtoMapper.class)
public interface RoleToRoleDtoMapper {

    @Mapping(target = "id", source = "id.value")
    @Mapping(target = "permissions", expression = "java(map(map(source.permissions())))")
    RoleDto map(Role source);

    List<PermissionDto> map(List<Permission> source);

    /** The permissions as a list sorted by key, for the overload above to map. */
    default List<Permission> map(Set<Permission> source) {
        return source.stream().sorted(Comparator.comparing(Permission::key)).toList();
    }
}
