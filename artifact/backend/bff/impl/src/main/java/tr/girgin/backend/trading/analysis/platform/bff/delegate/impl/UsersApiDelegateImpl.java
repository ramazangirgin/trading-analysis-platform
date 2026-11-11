package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.UsersApiDelegate;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.error.ApiException;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.CreateUserRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.CreatedUserDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.OneTimePasswordDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.ResetPasswordRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.UpdateUserRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.UserDto;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.RequiresPermission;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.UserSessions;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.UserAccountToUserDtoMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.ManageUsersPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UnlockUserPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.CreatedUserAccount;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserAccount;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;

@Service
class UsersApiDelegateImpl implements UsersApiDelegate {

    private final ManageUsersPort manageUsersPort;
    private final UnlockUserPort unlockUserPort;
    private final UserSessions userSessions;
    private final UserAccountToUserDtoMapper userAccountToUserDtoMapper;

    UsersApiDelegateImpl(
            ManageUsersPort manageUsersPort,
            UnlockUserPort unlockUserPort,
            UserSessions userSessions,
            UserAccountToUserDtoMapper userAccountToUserDtoMapper) {
        this.manageUsersPort = manageUsersPort;
        this.unlockUserPort = unlockUserPort;
        this.userSessions = userSessions;
        this.userAccountToUserDtoMapper = userAccountToUserDtoMapper;
    }

    @Override
    @RequiresPermission(Permission.USER_READ)
    public List<UserDto> listUsers() {
        return manageUsersPort.list().stream()
                .map(userAccountToUserDtoMapper::map)
                .toList();
    }

    @Override
    @RequiresPermission(Permission.USER_READ)
    public UserDto getUser(String id) {
        return userAccountToUserDtoMapper.map(manageUsersPort.get(new UserId(id)));
    }

    @Override
    @RequiresPermission(Permission.USER_MANAGE)
    public ResponseEntity<CreatedUserDto> createUser(CreateUserRequest request) {
        CreatedUserAccount created =
                manageUsersPort.create(request.username(), roleIds(request.roleIds()), password(request.password()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreatedUserDto(
                        userAccountToUserDtoMapper.map(created.account()),
                        created.generatedPassword().orElse(null)));
    }

    @Override
    @RequiresPermission(Permission.USER_MANAGE)
    public UserDto updateUser(String id, UpdateUserRequest request, HttpServletRequest http) {
        if (request.roleIds() == null && request.enabled() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "invalid_request",
                    "Give roleIds, enabled or both",
                    Map.of("fields", List.of("roleIds", "enabled")));
        }
        UserId target = new UserId(id);
        Set<RoleId> rolesBefore = roleIdsOf(manageUsersPort.get(target));
        UserAccount updated = manageUsersPort.update(
                currentUserId(),
                target,
                Optional.ofNullable(request.roleIds()).map(this::roleIds),
                Optional.ofNullable(request.enabled()),
                request.version());
        if (Boolean.FALSE.equals(request.enabled())) {
            userSessions.endAll(target);
        } else if (!roleIdsOf(updated).equals(rolesBefore)) {
            // A session keeps the authorities of its login: end the target's, but not the caller's own.
            userSessions.endAllExcept(target, sessionId(http));
        }
        return userAccountToUserDtoMapper.map(updated);
    }

    @Override
    @RequiresPermission(Permission.USER_MANAGE)
    public OneTimePasswordDto resetPassword(String id, ResetPasswordRequest request) {
        UserId target = new UserId(id);
        Optional<String> generated = manageUsersPort.resetPassword(target, password(request.password()));
        userSessions.endAll(target);
        return new OneTimePasswordDto(generated.orElse(null));
    }

    @Override
    @RequiresPermission(Permission.USER_MANAGE)
    public ResponseEntity<Void> unlock(String id) {
        unlockUserPort.unlock(new UserId(id));
        return ResponseEntity.noContent().build();
    }

    private Set<RoleId> roleIds(List<String> ids) {
        return ids.stream().map(RoleId::new).collect(Collectors.toSet());
    }

    private Set<RoleId> roleIdsOf(UserAccount account) {
        return account.roles().stream().map(Role::id).collect(Collectors.toSet());
    }

    private Optional<String> password(String password) {
        return Optional.ofNullable(password).filter(value -> !value.isBlank());
    }

    private String sessionId(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        return session == null ? null : session.getId();
    }

    // The principal of a session is the user ID string.
    private UserId currentUserId() {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        return new UserId((String) current.getPrincipal());
    }
}
