package tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound;

/** Makes sure someone can log in on a fresh installation. */
public interface CreateFirstAdminUseCase {

    /**
     * Creates the first admin when no user exists, and does nothing otherwise, so a restart never resets
     * a password. The user has the built-in ADMIN role. With a configured password it is used as it is;
     * without one a random password is generated, the user must change it, and it is logged once, at
     * WARN, after the user is saved. A configured password is never logged. An invalid configured
     * username fails with an {@code IdentityException} ({@code INVALID_USERNAME}). A configured password
     * must meet the password policy, otherwise this fails with an {@code IllegalStateException} that
     * names the broken rules, which stops the startup; it is checked only when a user is created. When
     * another instance creates the user at the same moment, this one does nothing.
     */
    void createFirstAdminIfNoUser();
}
