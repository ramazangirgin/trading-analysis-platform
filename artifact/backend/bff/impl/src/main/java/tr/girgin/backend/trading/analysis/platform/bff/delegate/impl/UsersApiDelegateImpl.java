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
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error.IdentityExceptionToApiExceptionMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.CreatedUserAccount;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.ManageUsersUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UnlockUserUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UserAccount;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;

@Service
class UsersApiDelegateImpl implements UsersApiDelegate {

    private final ManageUsersUseCase manageUsers;
    private final UnlockUserUseCase unlockUser;
    private final UserSessions userSessions;
    private final UserAccountToUserDtoMapper userMapper;
    private final IdentityExceptionToApiExceptionMapper errorMapper;

    UsersApiDelegateImpl(
            ManageUsersUseCase manageUsers,
            UnlockUserUseCase unlockUser,
            UserSessions userSessions,
            UserAccountToUserDtoMapper userMapper,
            IdentityExceptionToApiExceptionMapper errorMapper) {
        this.manageUsers = manageUsers;
        this.unlockUser = unlockUser;
        this.userSessions = userSessions;
        this.userMapper = userMapper;
        this.errorMapper = errorMapper;
    }

    @Override
    @RequiresPermission(Permission.USER_READ)
    public ResponseEntity<List<UserDto>> listUsers() {
        return ResponseEntity.ok(
                manageUsers.list().stream().map(userMapper::map).toList());
    }

    @Override
    @RequiresPermission(Permission.USER_READ)
    public ResponseEntity<UserDto> getUser(String id) {
        try {
            return ResponseEntity.ok(userMapper.map(manageUsers.get(new UserId(id))));
        } catch (IdentityException e) {
            throw errorMapper.map(e);
        }
    }

    @Override
    @RequiresPermission(Permission.USER_MANAGE)
    public ResponseEntity<CreatedUserDto> createUser(CreateUserRequest request) {
        try {
            CreatedUserAccount created =
                    manageUsers.create(request.username(), roleIds(request.roleIds()), password(request.password()));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new CreatedUserDto(
                            userMapper.map(created.account()),
                            created.generatedPassword().orElse(null)));
        } catch (IdentityException e) {
            throw errorMapper.map(e);
        }
    }

    @Override
    @RequiresPermission(Permission.USER_MANAGE)
    public ResponseEntity<UserDto> updateUser(String id, UpdateUserRequest request, HttpServletRequest http) {
        if (request.roleIds() == null && request.enabled() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "invalid_request",
                    "Give roleIds, enabled or both",
                    Map.of("fields", List.of("roleIds", "enabled")));
        }
        UserId target = new UserId(id);
        try {
            Set<RoleId> rolesBefore = roleIdsOf(manageUsers.get(target));
            UserAccount updated = manageUsers.update(
                    currentUserId(),
                    target,
                    Optional.ofNullable(request.roleIds()).map(UsersApiDelegateImpl::roleIds),
                    Optional.ofNullable(request.enabled()),
                    request.version());
            if (Boolean.FALSE.equals(request.enabled())) {
                userSessions.endAll(target);
            } else if (!roleIdsOf(updated).equals(rolesBefore)) {
                // A session keeps the authorities of its login: end the target's, but not the caller's own.
                userSessions.endAllExcept(target, sessionId(http));
            }
            return ResponseEntity.ok(userMapper.map(updated));
        } catch (IdentityException e) {
            throw errorMapper.map(e);
        }
    }

    @Override
    @RequiresPermission(Permission.USER_MANAGE)
    public ResponseEntity<OneTimePasswordDto> resetPassword(String id, ResetPasswordRequest request) {
        UserId target = new UserId(id);
        try {
            Optional<String> generated = manageUsers.resetPassword(target, password(request.password()));
            userSessions.endAll(target);
            return ResponseEntity.ok(new OneTimePasswordDto(generated.orElse(null)));
        } catch (IdentityException e) {
            throw errorMapper.map(e);
        }
    }

    @Override
    @RequiresPermission(Permission.USER_MANAGE)
    public ResponseEntity<Void> unlock(String id) {
        try {
            unlockUser.unlock(new UserId(id));
        } catch (IdentityException e) {
            throw errorMapper.map(e);
        }
        return ResponseEntity.noContent().build();
    }

    private static Set<RoleId> roleIds(List<String> ids) {
        return ids.stream().map(RoleId::new).collect(Collectors.toSet());
    }

    private static Set<RoleId> roleIdsOf(UserAccount account) {
        return account.roles().stream().map(Role::id).collect(Collectors.toSet());
    }

    private static Optional<String> password(String password) {
        return Optional.ofNullable(password).filter(value -> !value.isBlank());
    }

    private static String sessionId(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        return session == null ? null : session.getId();
    }

    // The principal of a session is the user ID string.
    private static UserId currentUserId() {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        return new UserId((String) current.getPrincipal());
    }
}
