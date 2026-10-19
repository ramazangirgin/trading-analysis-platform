package tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound;

import java.util.Optional;

/**
 * A user that was just created.
 *
 * @param generatedPassword the one-time password, when the core generated it; empty when the admin entered one
 */
public record CreatedUserAccount(UserAccount account, Optional<String> generatedPassword) {}
