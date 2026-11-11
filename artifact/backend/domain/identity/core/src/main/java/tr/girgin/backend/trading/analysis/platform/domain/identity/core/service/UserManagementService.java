package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.ManageUsersPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.CreatedUserAccount;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserAccount;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.RoleRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

/**
 * Creates and changes accounts for admins.
 *
 * <p>Two admins who disable each other at the same moment would each see the other as "another enabled ADMIN",
 * and the platform would be left with none. So every update that touches the enabled flag or the roles runs its
 * check and its write under one lock. That is enough because the platform runs as one instance: the sessions and
 * the login-attempt counts live in memory as well.
 *
 */
@Service
class UserManagementService implements ManageUsersPort {

    private static final Logger log = LoggerFactory.getLogger(UserManagementService.class);

    private final UserRepositoryPort userRepositoryPort;
    private final RoleRepositoryPort roleRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;
    private final PasswordPolicyService passwordPolicyService;
    private final OneTimePasswordService oneTimePasswordService;
    private final Clock clock;
    private final UserChangeService userChangeService;
    private final UsernameService usernameService;
    private final LoginLockoutService loginLockoutService;
    private final ReentrantLock adminGuard = new ReentrantLock();

    @Autowired
    @SuppressWarnings("checkstyle:ParameterNumber") // constructor injection of the collaborators above
    UserManagementService(
            UserRepositoryPort userRepositoryPort,
            RoleRepositoryPort roleRepositoryPort,
            PasswordHasherPort passwordHasherPort,
            PasswordPolicyService passwordPolicyService,
            OneTimePasswordService oneTimePasswordService,
            Clock clock,
            UserChangeService userChangeService,
            UsernameService usernameService,
            LoginLockoutService loginLockoutService) {
        this.userRepositoryPort = userRepositoryPort;
        this.roleRepositoryPort = roleRepositoryPort;
        this.passwordHasherPort = passwordHasherPort;
        this.passwordPolicyService = passwordPolicyService;
        this.oneTimePasswordService = oneTimePasswordService;
        this.clock = clock;
        this.userChangeService = userChangeService;
        this.usernameService = usernameService;
        this.loginLockoutService = loginLockoutService;
    }

    @Override
    public List<UserAccount> list() {
        Map<RoleId, Role> byId = rolesById();
        Instant now = clock.instant();
        return userRepositoryPort.findAll().stream()
                .map(user -> toAccount(user, byId, now))
                .toList();
    }

    @Override
    public UserAccount get(UserId id) {
        return toAccount(find(id), rolesById(), clock.instant());
    }

    @Override
    public CreatedUserAccount create(String username, Set<RoleId> roleIds, Optional<String> password) {
        Username name = usernameService.username(username);
        Map<RoleId, Role> byId = rolesById();
        requireKnown(roleIds, byId);
        Optional<String> entered = password.filter(p -> !p.isBlank());
        entered.ifPresent(p -> passwordPolicyService.check(p, name));
        Optional<String> generated = entered.isPresent() ? Optional.empty() : Optional.of(generate());
        String plain = entered.orElseGet(generated::orElseThrow);
        User toSave = new User(
                userChangeService.newId(),
                name,
                passwordHasherPort.hash(plain),
                true,
                true,
                0,
                Optional.empty(),
                Optional.empty(),
                null,
                null,
                Set.copyOf(roleIds),
                null);
        User saved;
        try {
            saved = userRepositoryPort.save(toSave);
        } catch (DuplicateKeyException _) {
            throw new IdentityException(
                    IdentityError.USERNAME_TAKEN,
                    "The username \"" + name.value() + "\" is taken",
                    Map.of("username", name.value()));
        }
        log.info(
                "User {} created with roles {}, password {}",
                saved.id().value(),
                roleNames(roleIds),
                generated.isPresent() ? "generated" : "entered");
        return new CreatedUserAccount(toAccount(saved, byId, clock.instant()), generated);
    }

    @Override
    public UserAccount update(
            UserId actor,
            UserId target,
            Optional<Set<RoleId>> roleIds,
            Optional<Boolean> enabled,
            Long expectedVersion) {
        Map<RoleId, Role> byId = rolesById();
        roleIds.ifPresent(ids -> requireKnown(ids, byId));
        adminGuard.lock();
        try {
            User user = find(target);
            Set<RoleId> newRoles = roleIds.orElse(user.roleIds());
            boolean newEnabled = enabled.orElse(user.enabled());
            checkNotSelfLockout(actor, user, newRoles, newEnabled);
            if (expectedVersion != null && !expectedVersion.equals(user.version())) {
                throw concurrentUpdate(target, null);
            }
            checkAnAdminRemains(user, newRoles, newEnabled);
            boolean rolesChanged = !newRoles.equals(user.roleIds());
            boolean enabledChanged = newEnabled != user.enabled();
            if (!rolesChanged && !enabledChanged) {
                return toAccount(user, byId, clock.instant());
            }
            User saved = save(userChangeService.withEnabled(userChangeService.withRoleIds(user, newRoles), newEnabled));
            if (rolesChanged) {
                log.info("Roles of user {} changed to {}", target.value(), roleNames(newRoles));
            }
            if (enabledChanged) {
                log.info("User {} {}", target.value(), newEnabled ? "enabled" : "disabled");
            }
            return toAccount(saved, byId, clock.instant());
        } finally {
            adminGuard.unlock();
        }
    }

    private void checkNotSelfLockout(UserId actor, User user, Set<RoleId> newRoles, boolean newEnabled) {
        if (!actor.equals(user.id())) {
            return;
        }
        if (!newEnabled) {
            throw new IdentityException(
                    IdentityError.CANNOT_DISABLE_SELF,
                    "Admins cannot disable themselves",
                    Map.of("id", user.id().value()));
        }
        if (losesAdmin(user, newRoles)) {
            throw new IdentityException(
                    IdentityError.CANNOT_REMOVE_OWN_ADMIN,
                    "Admins cannot remove their own ADMIN role",
                    Map.of("id", user.id().value()));
        }
    }

    private void checkAnAdminRemains(User user, Set<RoleId> newRoles, boolean newEnabled) {
        boolean leavesAdmins = user.enabled() && (!newEnabled || losesAdmin(user, newRoles));
        if (leavesAdmins
                && user.roleIds().contains(RoleId.ADMIN)
                && userRepositoryPort.countEnabledWithRole(RoleId.ADMIN) - 1 < 1) {
            throw new IdentityException(
                    IdentityError.LAST_ADMIN,
                    "The last enabled ADMIN cannot be disabled or lose the ADMIN role",
                    Map.of("id", user.id().value()));
        }
    }

    private boolean losesAdmin(User user, Set<RoleId> newRoles) {
        return user.roleIds().contains(RoleId.ADMIN) && !newRoles.contains(RoleId.ADMIN);
    }

    @Override
    public Optional<String> resetPassword(UserId target, Optional<String> password) {
        User user = find(target);
        Optional<String> entered = password.filter(p -> !p.isBlank());
        entered.ifPresent(p -> passwordPolicyService.check(p, user.username()));
        Optional<String> generated = entered.isPresent() ? Optional.empty() : Optional.of(generate());
        String plain = entered.orElseGet(generated::orElseThrow);
        save(loginLockoutService.unlocked(userChangeService.withPassword(user, passwordHasherPort.hash(plain), true)));
        log.info(
                "Password of user {} reset, password {}",
                target.value(),
                generated.isPresent() ? "generated" : "entered");
        return generated;
    }

    private String generate() {
        return oneTimePasswordService.generate(
                Math.max(OneTimePasswordService.DEFAULT_LENGTH, passwordPolicyService.minLength()));
    }

    private User find(UserId id) {
        return userRepositoryPort
                .findById(id)
                .orElseThrow(() -> new IdentityException(
                        IdentityError.USER_NOT_FOUND, "User " + id.value() + " not found", Map.of("id", id.value())));
    }

    private User save(User user) {
        try {
            return userRepositoryPort.save(user);
        } catch (OptimisticLockingFailureException e) {
            throw concurrentUpdate(user.id(), e);
        }
    }

    private IdentityException concurrentUpdate(UserId id, OptimisticLockingFailureException cause) {
        IdentityException exception = new IdentityException(
                IdentityError.CONCURRENT_UPDATE,
                "User " + id.value() + " was changed by someone else",
                Map.of("id", id.value()));
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }

    private Map<RoleId, Role> rolesById() {
        return roleRepositoryPort.findAll().stream().collect(Collectors.toMap(Role::id, Function.identity()));
    }

    private void requireKnown(Set<RoleId> roleIds, Map<RoleId, Role> byId) {
        for (RoleId id : roleIds) {
            if (!byId.containsKey(id)) {
                throw new IdentityException(
                        IdentityError.UNKNOWN_ROLE, "Unknown role " + id.value(), Map.of("roleId", id.value()));
            }
        }
    }

    private List<String> roleNames(Set<RoleId> roleIds) {
        return roleIds.stream().map(RoleId::value).sorted().toList();
    }

    private UserAccount toAccount(User user, Map<RoleId, Role> byId, Instant now) {
        List<Role> userRoles = user.roleIds().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Role::name))
                .toList();
        return new UserAccount(
                user.id(),
                user.username(),
                userRoles,
                user.enabled(),
                user.mustChangePassword(),
                loginLockoutService.isLocked(user, now),
                user.lockedUntil(),
                user.lastLoginAt(),
                user.createdAt(),
                user.updatedAt(),
                user.version());
    }
}
