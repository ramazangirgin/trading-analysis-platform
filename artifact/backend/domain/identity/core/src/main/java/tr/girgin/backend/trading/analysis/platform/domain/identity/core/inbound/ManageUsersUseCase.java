package tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;

/** What admins do with accounts. There is no hard delete: a user is disabled instead. */
public interface ManageUsersUseCase {

    /** All users, sorted by username, ignoring case. */
    List<UserAccount> list();

    /**
     * Finds one user.
     *
     * @throws tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException
     *     {@code USER_NOT_FOUND}
     */
    UserAccount get(UserId id);

    /**
     * Creates an enabled user who must change the password at the first login.
     *
     * @param password the one-time password; empty to have one generated
     * @throws tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException
     *     {@code INVALID_USERNAME}, {@code UNKNOWN_ROLE}, {@code WEAK_PASSWORD} or {@code USERNAME_TAKEN}
     */
    CreatedUserAccount create(String username, Set<RoleId> roleIds, Optional<String> password);

    /**
     * Changes the roles and the enabled flag of a user; an empty argument leaves its part unchanged, and an
     * update that changes nothing writes nothing. At least one enabled ADMIN always remains, and an actor can
     * neither disable themselves nor remove their own ADMIN role.
     *
     * @param expectedVersion the version the caller read; {@code null} to apply the change to what is stored
     * @throws tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException
     *     {@code USER_NOT_FOUND}, {@code UNKNOWN_ROLE}, {@code CANNOT_DISABLE_SELF},
     *     {@code CANNOT_REMOVE_OWN_ADMIN}, {@code LAST_ADMIN} or {@code CONCURRENT_UPDATE}
     */
    UserAccount update(
            UserId actor,
            UserId target,
            Optional<Set<RoleId>> roleIds,
            Optional<Boolean> enabled,
            Long expectedVersion);

    /**
     * Sets a new one-time password: the user must change it at the next login. Clears the lock and the count of
     * failed logins; does not enable a disabled user.
     *
     * @param password the new password; empty to have one generated
     * @return the generated password, empty when the admin entered one
     * @throws tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException
     *     {@code USER_NOT_FOUND}, {@code WEAK_PASSWORD} or {@code CONCURRENT_UPDATE}
     */
    Optional<String> resetPassword(UserId target, Optional<String> password);
}
