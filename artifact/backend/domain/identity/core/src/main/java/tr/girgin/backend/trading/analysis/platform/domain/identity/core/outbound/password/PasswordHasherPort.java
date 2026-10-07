package tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password;

import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;

/** Hashes passwords and checks them against stored hashes. */
public interface PasswordHasherPort {

    PasswordHash hash(CharSequence rawPassword);

    boolean matches(CharSequence rawPassword, PasswordHash hash);

    /** Whether the hash was made with an algorithm other than the current default, so a login can upgrade it. */
    boolean needsRehash(PasswordHash hash);
}
