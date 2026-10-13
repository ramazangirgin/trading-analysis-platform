package tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;

/** Lifts the lockout of an account before it ends. */
public interface UnlockUserUseCase {

    /**
     * Clears the lock and the count of failed logins. Unlocking a user that is not locked succeeds and
     * changes nothing.
     *
     * @throws tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException
     *     {@code USER_NOT_FOUND} when there is no such user
     */
    void unlock(UserId id);
}
