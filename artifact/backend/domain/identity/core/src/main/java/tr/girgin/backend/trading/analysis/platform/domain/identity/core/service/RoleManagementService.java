package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.ManageRolesUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.RoleSummary;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.RoleRepositoryPort;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

/**
 * Creates and changes roles for admins. Role names are ASCII only, like usernames, because which names are equal
 * ignoring case must not depend on the database's locale.
 */
@Service
class RoleManagementService implements ManageRolesUseCase {

    private static final Logger log = LoggerFactory.getLogger(RoleManagementService.class);

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9 _-]{2,40}");
    private static final int MAX_DESCRIPTION_LENGTH = 200;

    private final RoleRepositoryPort roles;
    private final UserRepositoryPort users;

    RoleManagementService(RoleRepositoryPort roles, UserRepositoryPort users) {
        this.roles = roles;
        this.users = users;
    }

    @Override
    public List<RoleSummary> list() {
        Map<RoleId, Long> counts = users.countUsersByRole();
        return roles.findAll().stream()
                .sorted(Comparator.comparing(Role::name))
                .map(role -> new RoleSummary(role, counts.getOrDefault(role.id(), 0L)))
                .toList();
    }

    @Override
    public RoleSummary get(RoleId id) {
        Role role = find(id);
        return new RoleSummary(role, users.countWithRole(id));
    }

    @Override
    public RoleSummary create(String name, String description, Set<Permission> permissions) {
        String validName = validName(name);
        String validDescription = validDescription(description);
        requireNameFree(validName, null);
        Role saved;
        try {
            saved = roles.save(new Role(RoleId.newId(), validName, false, validDescription, permissions, null));
        } catch (DuplicateKeyException e) {
            throw nameTaken(validName, e);
        }
        log.info("Role {} created as \"{}\" with permissions {}", saved.id().value(), saved.name(), keys(permissions));
        return new RoleSummary(saved, 0);
    }

    @Override
    public RoleSummary update(
            RoleId id,
            Optional<String> name,
            Optional<String> description,
            Optional<Set<Permission>> permissions,
            Long expectedVersion) {
        Role role = find(id);
        if (expectedVersion != null && !expectedVersion.equals(role.version())) {
            throw concurrentUpdate(id, null);
        }
        Optional<String> newName = name.map(String::trim).filter(n -> !n.equals(role.name()));
        Optional<String> newDescription = description.map(String::trim).filter(d -> !d.equals(role.description()));
        Optional<Set<Permission>> newPermissions = permissions.filter(p -> !p.equals(role.permissions()));
        if (role.builtIn()) {
            newName.ifPresent(_ -> throwBuiltIn(id, "rename"));
            newDescription.ifPresent(_ -> throwBuiltIn(id, "description"));
        }
        if (RoleId.ADMIN.equals(id)) {
            newPermissions.ifPresent(_ -> throwBuiltIn(id, "permissions"));
        }
        Role changed = role;
        if (newName.isPresent()) {
            changed = changed.withName(freeName(newName.get(), id));
        }
        if (newDescription.isPresent()) {
            changed = changed.withDescription(validDescription(newDescription.get()));
        }
        if (newPermissions.isPresent()) {
            changed = changed.withPermissions(newPermissions.get());
        }
        if (changed.equals(role)) {
            return new RoleSummary(role, users.countWithRole(id));
        }
        Role saved = save(changed);
        logChanges(role, saved);
        return new RoleSummary(saved, users.countWithRole(id));
    }

    private void logChanges(Role before, Role after) {
        String id = before.id().value();
        if (!after.name().equals(before.name())) {
            log.info("Role {} renamed from \"{}\" to \"{}\"", id, before.name(), after.name());
        }
        if (!after.description().equals(before.description())) {
            log.info("Description of role {} changed", id);
        }
        if (!after.permissions().equals(before.permissions())) {
            log.info("Permissions of role {} changed to {}", id, keys(after.permissions()));
        }
    }

    private String freeName(String name, RoleId self) {
        String valid = validName(name);
        requireNameFree(valid, self);
        return valid;
    }

    @Override
    public void delete(RoleId id) {
        Role role = find(id);
        if (role.builtIn()) {
            throwBuiltIn(id, "delete");
        }
        long userCount = users.countWithRole(id);
        if (userCount > 0) {
            throw inUse(id, userCount, null);
        }
        try {
            roles.deleteById(id);
        } catch (DataIntegrityViolationException e) {
            throw inUse(id, users.countWithRole(id), e);
        }
        log.info("Role {} (\"{}\") deleted", id.value(), role.name());
    }

    private Role find(RoleId id) {
        return roles.findById(id)
                .orElseThrow(() -> new IdentityException(
                        IdentityError.ROLE_NOT_FOUND, "Role " + id.value() + " not found", Map.of("id", id.value())));
    }

    private Role save(Role role) {
        try {
            return roles.save(role);
        } catch (OptimisticLockingFailureException e) {
            throw concurrentUpdate(role.id(), e);
        } catch (DuplicateKeyException e) {
            throw nameTaken(role.name(), e);
        }
    }

    private void requireNameFree(String name, RoleId self) {
        if (roles.findByName(name).filter(other -> !other.id().equals(self)).isPresent()) {
            throw nameTaken(name, null);
        }
    }

    private String validName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (!VALID_NAME.matcher(trimmed).matches()) {
            throw new IdentityException(
                    IdentityError.INVALID_ROLE_NAME,
                    "Role name must be 2 to 40 characters of letters, digits, spaces, '_' and '-'",
                    Map.of());
        }
        return trimmed;
    }

    private String validDescription(String description) {
        String trimmed = description == null ? "" : description.trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IdentityException(
                    IdentityError.INVALID_ROLE_DESCRIPTION,
                    "Role description must be at most " + MAX_DESCRIPTION_LENGTH + " characters",
                    Map.of());
        }
        return trimmed;
    }

    private void throwBuiltIn(RoleId id, String change) {
        throw new IdentityException(
                IdentityError.BUILT_IN_ROLE,
                "The built-in role " + id.value() + " cannot be changed: " + change,
                Map.of("id", id.value(), "change", change));
    }

    private IdentityException nameTaken(String name, Exception cause) {
        IdentityException exception = new IdentityException(
                IdentityError.ROLE_NAME_TAKEN, "The role name \"" + name + "\" is taken", Map.of("name", name));
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }

    private IdentityException inUse(RoleId id, long userCount, Exception cause) {
        IdentityException exception = new IdentityException(
                IdentityError.ROLE_IN_USE,
                "Role " + id.value() + " is held by " + userCount + " users",
                Map.of("id", id.value(), "userCount", userCount));
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }

    private IdentityException concurrentUpdate(RoleId id, OptimisticLockingFailureException cause) {
        IdentityException exception = new IdentityException(
                IdentityError.CONCURRENT_UPDATE,
                "Role " + id.value() + " was changed by someone else",
                Map.of("id", id.value()));
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }

    private List<String> keys(Set<Permission> permissions) {
        return permissions.stream().map(Permission::key).sorted().toList();
    }
}
