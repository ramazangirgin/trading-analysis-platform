package tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.AuthenticatedUser;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;

/** Lets a signed-in user set a new password. */
public interface ChangePasswordUseCase {

    /**
     * Stores the new password of the user and clears their change-password flag. The current password
     * must match, otherwise this fails with an {@code IdentityException} ({@code WRONG_CURRENT_PASSWORD})
     * and nothing is saved. The new one must meet the password policy (minimum and maximum length, not a
     * common password, not the username), otherwise this fails with {@code WEAK_PASSWORD} and the broken
     * rules in its params; it must also differ from the current one ({@code PASSWORD_UNCHANGED}). An
     * unknown or disabled user fails with {@code BAD_CREDENTIALS}, so the caller can end their session.
     * Returns the user as they are now. The passwords are never logged.
     */
    AuthenticatedUser changePassword(UserId id, CharSequence currentPassword, CharSequence newPassword);
}
