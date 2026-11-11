package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence;

import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper.RoleEntityToRoleMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper.RoleIdToRoleIdEmbeddableMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper.RoleToRoleEntityMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.RoleRepositoryPort;

@Component
class JpaRoleRepositoryAdapter implements RoleRepositoryPort {

    private final RoleJpaRepository roleJpaRepository;
    private final RoleToRoleEntityMapper roleToRoleEntityMapper;
    private final RoleEntityToRoleMapper roleEntityToRoleMapper;
    private final RoleIdToRoleIdEmbeddableMapper roleIdToRoleIdEmbeddableMapper;

    JpaRoleRepositoryAdapter(
            RoleJpaRepository roleJpaRepository,
            RoleToRoleEntityMapper roleToRoleEntityMapper,
            RoleEntityToRoleMapper roleEntityToRoleMapper,
            RoleIdToRoleIdEmbeddableMapper roleIdToRoleIdEmbeddableMapper) {
        this.roleJpaRepository = roleJpaRepository;
        this.roleToRoleEntityMapper = roleToRoleEntityMapper;
        this.roleEntityToRoleMapper = roleEntityToRoleMapper;
        this.roleIdToRoleIdEmbeddableMapper = roleIdToRoleIdEmbeddableMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> findById(RoleId id) {
        return roleJpaRepository
                .findById(roleIdToRoleIdEmbeddableMapper.map(id))
                .map(roleEntityToRoleMapper::map);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> findByName(String name) {
        return roleJpaRepository.findByName(name).map(roleEntityToRoleMapper::map);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Role> findAll() {
        return roleJpaRepository.findAllByOrderByNameAsc().stream()
                .map(roleEntityToRoleMapper::map)
                .toList();
    }

    /**
     * An upsert that replaces the permissions (an array on the row) in the same transaction. A role without a
     * version is persisted (an existing ID fails), any other is merged, and Hibernate rejects a version that
     * differs from the stored one.
     */
    @Override
    @Transactional
    public Role save(Role role) {
        try {
            return roleEntityToRoleMapper.map(roleJpaRepository.saveAndFlush(roleToRoleEntityMapper.map(role)));
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                throw new DuplicateKeyException("The role name is taken by another role", e);
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public void deleteById(RoleId id) {
        roleJpaRepository.deleteByIdValue(id.value());
    }

    private boolean isUniqueViolation(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) {
                return true;
            }
        }
        return false;
    }
}
