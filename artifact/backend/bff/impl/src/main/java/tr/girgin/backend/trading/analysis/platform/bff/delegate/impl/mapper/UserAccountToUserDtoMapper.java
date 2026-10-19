package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.UserDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.UserRoleDto;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UserAccount;

/** The core hands the roles out sorted by name, and the empty lock and last login become null. */
@Mapper
public interface UserAccountToUserDtoMapper {

    default UserDto map(UserAccount source) {
        return new UserDto(
                source.id().value(),
                source.username().value(),
                source.roles().stream()
                        .map(role -> new UserRoleDto(role.id().value(), role.name()))
                        .toList(),
                source.enabled(),
                source.mustChangePassword(),
                source.locked(),
                source.lockedUntil().orElse(null),
                source.lastLoginAt().orElse(null),
                source.createdAt(),
                source.updatedAt(),
                source.version());
    }
}
