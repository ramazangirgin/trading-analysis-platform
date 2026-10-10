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

    /**
     * Inserts or updates the role and replaces its permissions with {@link Role#permissions()}, in one
     * transaction. A role whose {@link Role#version()} differs from the stored one fails with Spring's
     * {@code OptimisticLockingFailureException} and leaves the row unchanged; a {@code null} version means
     * "insert", so it fails on an existing ID as well. Returns the stored role, with its new version.
     */
    Role save(Role role);
}
