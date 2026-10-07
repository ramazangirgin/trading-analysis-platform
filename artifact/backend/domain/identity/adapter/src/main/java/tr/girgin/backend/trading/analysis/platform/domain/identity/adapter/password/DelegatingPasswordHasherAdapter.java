package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.password;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.password.PasswordHasherPort;

/**
 * Spring Security's delegating encoder: new hashes are {@code {bcrypt}} (strength 10), and the
 * {@code {id}} prefix of a stored hash picks the algorithm that checks it, so the default can change
 * without touching stored hashes.
 */
@Component
class DelegatingPasswordHasherAdapter implements PasswordHasherPort {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Override
    public PasswordHash hash(CharSequence rawPassword) {
        return new PasswordHash(encoder.encode(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, PasswordHash hash) {
        return encoder.matches(rawPassword, hash.value());
    }

    @Override
    public boolean needsRehash(PasswordHash hash) {
        return encoder.upgradeEncoding(hash.value());
    }
}
