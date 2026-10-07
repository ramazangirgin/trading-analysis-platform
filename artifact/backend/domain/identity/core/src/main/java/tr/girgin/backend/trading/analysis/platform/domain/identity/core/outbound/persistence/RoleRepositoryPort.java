package tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence;

import java.util.List;
import java.util.Optional;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;

/** Stores roles together with their permissions. */
public interface RoleRepositoryPort {

    Optional<Role> findById(RoleId id);

    Optional<Role> findByName(String name);

    List<Role> findAll();

    /** Inserts or updates the role and replaces its permissions with {@link Role#permissions()}, in one transaction. */
    void save(Role role);
}
