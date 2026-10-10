package tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence;

import java.util.List;
import java.util.Optional;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;

/** Stores users together with their role assignments. */
public interface UserRepositoryPort {

    Optional<User> findById(UserId id);

    /** Finds a user by username, ignoring case. */
    Optional<User> findByUsername(Username username);

    List<User> findAll();

    long count();

    /**
     * Inserts or updates the user and replaces its role assignments with {@link User#roleIds()}, in
     * one transaction. A username taken by another user, in any case, fails with Spring's
     * {@code DuplicateKeyException}. Returns the stored user, with the audited {@code createdAt} and
     * {@code updatedAt} set by persistence and its new {@link User#version()}.
     *
     * <p>A user whose version differs from the stored one fails with Spring's
     * {@code OptimisticLockingFailureException} and leaves the row unchanged; a {@code null} version means
     * "insert", so it fails on an existing ID as well.
     */
    User save(User user);
}
