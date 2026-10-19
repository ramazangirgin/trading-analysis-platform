package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity.RoleEntity;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity.RoleIdEmbeddable;

/** The permissions of a role are an array on its row, so no finder needs an entity graph. */
interface RoleJpaRepository extends JpaRepository<RoleEntity, RoleIdEmbeddable> {

    /** Matches the {@code ROLES_NAME_LOWER_UK} index, which a derived {@code IgnoreCase} method would miss. */
    @Query("select r from RoleEntity r where lower(r.name) = lower(:name)")
    Optional<RoleEntity> findByName(@Param("name") String name);

    List<RoleEntity> findAllByOrderByNameAsc();

    /** Runs the {@code DELETE} at once, so the foreign key of an assigned role fails in the call. */
    @Modifying
    @Query("delete from RoleEntity r where r.id.value = :id")
    void deleteByIdValue(@Param("id") String id);
}
