package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence;

import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
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
     */
    @Override
    @Transactional
    public void save(User user) {
        try {
            repository.saveAndFlush(toEntity.map(user));
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                throw new DuplicateKeyException("The username is taken by another user", e);
            }
            throw e;
        }
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
