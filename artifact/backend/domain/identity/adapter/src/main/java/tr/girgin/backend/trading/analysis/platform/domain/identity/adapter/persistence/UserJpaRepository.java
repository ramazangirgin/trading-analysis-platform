package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity.UserEntity;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity.UserIdEmbeddable;

/** The finders load the role assignments with the user, so a read is one query per call. */
interface UserJpaRepository extends JpaRepository<UserEntity, UserIdEmbeddable> {

    @Override
    @EntityGraph(attributePaths = "roleIds")
    Optional<UserEntity> findById(UserIdEmbeddable id);

    /** Matches the {@code USERS_USERNAME_LOWER_UK} index, which a derived {@code IgnoreCase} method would miss. */
    @EntityGraph(attributePaths = "roleIds")
    @Query("select u from UserEntity u where lower(u.username) = lower(:username)")
    Optional<UserEntity> findByUsernameIgnoringCase(@Param("username") String username);

    @EntityGraph(attributePaths = "roleIds")
    @Query("select u from UserEntity u order by lower(u.username)")
    List<UserEntity> findAllOrderedByUsername();
}
