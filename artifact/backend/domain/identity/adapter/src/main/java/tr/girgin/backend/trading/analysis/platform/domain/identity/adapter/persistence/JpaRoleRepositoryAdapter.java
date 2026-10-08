package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence;

import java.util.List;
import java.util.Optional;
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

    private final RoleJpaRepository repository;
    private final RoleToRoleEntityMapper toEntity;
    private final RoleEntityToRoleMapper toRole;
    private final RoleIdToRoleIdEmbeddableMapper toEntityId;

    JpaRoleRepositoryAdapter(
            RoleJpaRepository repository,
            RoleToRoleEntityMapper toEntity,
            RoleEntityToRoleMapper toRole,
            RoleIdToRoleIdEmbeddableMapper toEntityId) {
        this.repository = repository;
        this.toEntity = toEntity;
        this.toRole = toRole;
        this.toEntityId = toEntityId;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> findById(RoleId id) {
        return repository.findById(toEntityId.map(id)).map(toRole::map);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> findByName(String name) {
        return repository.findByName(name).map(toRole::map);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Role> findAll() {
        return repository.findAllByOrderByNameAsc().stream().map(toRole::map).toList();
    }

    /** An upsert that replaces the permissions (an array on the row) in the same transaction. */
    @Override
    @Transactional
    public void save(Role role) {
        repository.saveAndFlush(toEntity.map(role));
    }
}
