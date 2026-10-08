package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity.RoleEntity;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity.RoleIdEmbeddable;

/** The permissions of a role are an array on its row, so no finder needs an entity graph. */
interface RoleJpaRepository extends JpaRepository<RoleEntity, RoleIdEmbeddable> {

    Optional<RoleEntity> findByName(String name);

    List<RoleEntity> findAllByOrderByNameAsc();
}
