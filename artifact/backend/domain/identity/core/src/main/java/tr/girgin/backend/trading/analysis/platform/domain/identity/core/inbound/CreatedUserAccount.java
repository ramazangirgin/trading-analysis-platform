package tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound;

import java.util.Optional;

/** A user that was just created, with the one-time password when the core generated it. */
public record CreatedUserAccount(UserAccount account, Optional<String> generatedPassword) {}
