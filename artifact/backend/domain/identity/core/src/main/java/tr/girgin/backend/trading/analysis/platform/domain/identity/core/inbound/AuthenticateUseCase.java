package tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound;

import java.util.Optional;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.AuthenticatedUser;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;

/** Checks a username and password, and looks up a signed-in user again. */
public interface AuthenticateUseCase {

    /**
     * Checks the credentials. A username that is not a valid one, an unknown user, a disabled user and a
     * wrong password all fail the same way: an {@code IdentityException} with {@code BAD_CREDENTIALS}, the
     * same message and no parameters, and about the same response time, so a caller cannot tell which it was.
     * A matching password may be stored again with a newer hash.
     */
    AuthenticatedUser authenticate(String username, CharSequence password);

    /** The user as they are now; empty when they no longer exist or are disabled. */
    Optional<AuthenticatedUser> findActiveUser(UserId id);
}
