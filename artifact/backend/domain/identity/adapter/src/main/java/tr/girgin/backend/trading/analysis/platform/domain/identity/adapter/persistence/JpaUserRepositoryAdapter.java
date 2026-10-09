package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity.UserEntity;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper.UserEntityToUserMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper.UserIdToUserIdEmbeddableMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper.UserToUserEntityMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

@Component
class JpaUserRepositoryAdapter implements UserRepositoryPort {

    private final UserJpaRepository repository;
    private final UserToUserEntityMapper toEntity;
    private final UserEntityToUserMapper toUser;
    private final UserIdToUserIdEmbeddableMapper toEntityId;

    JpaUserRepositoryAdapter(
            UserJpaRepository repository,
            UserToUserEntityMapper toEntity,
            UserEntityToUserMapper toUser,
            UserIdToUserIdEmbeddableMapper toEntityId) {
        this.repository = repository;
        this.toEntity = toEntity;
        this.toUser = toUser;
        this.toEntityId = toEntityId;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UserId id) {
        return repository.findById(toEntityId.map(id)).map(toUser::map);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(Username username) {
        return repository.findByUsernameIgnoringCase(username.value()).map(toUser::map);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return repository.findAllOrderedByUsername().stream().map(toUser::map).toList();
    }

    @Override
    public long count() {
        return repository.count();
    }

    /**
     * An upsert that replaces the role assignments in the same transaction. The flush raises a
     * unique or foreign-key violation inside this call. Spring translates both to a
     * {@link DataIntegrityViolationException}; the port promises a {@link DuplicateKeyException} for
     * a taken username, so a unique violation is rethrown as that.
     *
     * <p>Not a plain merge: the incoming entity has {@code createdAt == null}, and a merge would copy that onto
     * the stored row's managed entity, so the returned user would lose its creation time. An existing user is
     * loaded and the domain fields are copied onto it instead. Its {@code updatedAt} is cleared so the flush
     * always updates the row and auditing sets the time, also when nothing else changed.
     *
     * <p>A managed entity's version cannot be changed, so the incoming version is compared with the loaded one
     * before the copy: a difference (a {@code null} version included) means the record is stale and fails with an
     * {@link OptimisticLockingFailureException}, as does a version for a row that is gone. The flush's
     * {@code UPDATE ... WHERE VERSION = ?} covers the time between the load and the flush.
     */
    @Override
    @Transactional
    public User save(User user) {
        try {
            UserEntity incoming = toEntity.map(user);
            UserEntity stored = repository
                    .findById(incoming.getId())
                    .map(existing -> copyOnto(existing, incoming))
                    .orElseGet(() -> insert(incoming));
            repository.flush();
            return toUser.map(stored);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                throw new DuplicateKeyException("The username is taken by another user", e);
            }
            throw e;
        }
    }

    private UserEntity insert(UserEntity incoming) {
        if (incoming.getVersion() != null) {
            throw new OptimisticLockingFailureException(
                    "User " + incoming.getId().getValue() + " was deleted since it was read");
        }
        return repository.save(incoming);
    }

    private static UserEntity copyOnto(UserEntity existing, UserEntity incoming) {
        if (!Objects.equals(existing.getVersion(), incoming.getVersion())) {
            throw new OptimisticLockingFailureException(
                    "User " + existing.getId().getValue() + " was changed by someone else since it was read");
        }
        existing.setUsername(incoming.getUsername());
        existing.setPasswordHash(incoming.getPasswordHash());
        existing.setEnabled(incoming.isEnabled());
        existing.setMustChangePassword(incoming.isMustChangePassword());
        existing.setFailedLoginCount(incoming.getFailedLoginCount());
        existing.setLockedUntil(incoming.getLockedUntil());
        existing.setUpdatedAt(null);
        existing.getRoleIds().clear();
        existing.getRoleIds().addAll(incoming.getRoleIds());
        return existing;
    }

    private static boolean isUniqueViolation(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) {
                return true;
            }
        }
        return false;
    }
}
