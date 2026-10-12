package tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound;

/** Makes sure someone can log in on a fresh installation. */
public interface CreateFirstAdminUseCase {

    /**
     * Creates the first admin when no user exists, and does nothing otherwise, so a restart never resets
     * a password. The user has the built-in ADMIN role. With a configured password it is used as it is;
     * without one a random password is generated, the user must change it, and it is logged once, at
     * WARN, after the user is saved. A configured password is never logged. An invalid configured
     * username fails with an {@code IdentityException} ({@code INVALID_USERNAME}). When another
     * instance creates the user at the same moment, this one does nothing.
     *
     * <p>The password policy check for a configured password (#84) belongs in the implementation.
     */
    void createFirstAdminIfNoUser();
}
