package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.RoleRepositoryPort;

/** A role store like the real one: versions count up, names match ignoring case, and failures can be injected. */
final class InMemoryRoleRepository implements RoleRepositoryPort {

    private final Map<RoleId, Role> roles = new HashMap<>();
    private final List<Role> saved = new ArrayList<>();
    private final List<RoleId> deleted = new ArrayList<>();
    private RuntimeException saveFailure;
    private RuntimeException deleteFailure;

    /** Puts a role into the store without counting it as a save. */
    Role add(Role role) {
        roles.put(role.id(), role);
        return role;
    }

    List<Role> saved() {
        return saved;
    }

    List<RoleId> deleted() {
        return deleted;
    }

    void failSavesWith(RuntimeException failure) {
        saveFailure = failure;
    }

    void failDeletesWith(RuntimeException failure) {
        deleteFailure = failure;
    }

    @Override
    public Optional<Role> findById(RoleId id) {
        return Optional.ofNullable(roles.get(id));
    }

    @Override
    public Optional<Role> findByName(String name) {
        return roles.values().stream()
                .filter(r -> r.name().equalsIgnoreCase(name))
                .findFirst();
    }

    @Override
    public List<Role> findAll() {
        return new ArrayList<>(roles.values());
    }

    @Override
    public Role save(Role role) {
        if (saveFailure != null) {
            throw saveFailure;
        }
        Role stored = new Role(
                role.id(),
                role.name(),
                role.builtIn(),
                role.description(),
                role.permissions(),
                role.version() == null ? 0L : role.version() + 1);
        saved.add(stored);
        roles.put(stored.id(), stored);
        return stored;
    }

    @Override
    public void deleteById(RoleId id) {
        if (deleteFailure != null) {
            throw deleteFailure;
        }
        deleted.add(id);
        roles.remove(id);
    }
}
