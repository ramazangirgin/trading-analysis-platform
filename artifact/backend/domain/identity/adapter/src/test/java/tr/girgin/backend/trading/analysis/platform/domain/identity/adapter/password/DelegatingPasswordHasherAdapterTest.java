package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.password;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;

class DelegatingPasswordHasherAdapterTest {

    private final DelegatingPasswordHasherAdapter delegatingPasswordHasherAdapter =
            new DelegatingPasswordHasherAdapter();

    @Test
    void hashStartsWithBcryptIdAndDiffersFromThePassword() {
        PasswordHash hash = delegatingPasswordHasherAdapter.hash("correct horse");

        assertThat(hash.value()).startsWith("{bcrypt}").isNotEqualTo("correct horse");
    }

    @Test
    void matchesTheRightPasswordOnly() {
        PasswordHash hash = delegatingPasswordHasherAdapter.hash("correct horse");

        assertThat(delegatingPasswordHasherAdapter.matches("correct horse", hash))
                .isTrue();
        assertThat(delegatingPasswordHasherAdapter.matches("wrong horse", hash)).isFalse();
    }

    @Test
    void twoHashesOfTheSamePasswordDiffer() {
        assertThat(delegatingPasswordHasherAdapter.hash("same"))
                .isNotEqualTo(delegatingPasswordHasherAdapter.hash("same"));
    }

    @Test
    void aFreshHashNeedsNoRehash() {
        assertThat(delegatingPasswordHasherAdapter.needsRehash(delegatingPasswordHasherAdapter.hash("password")))
                .isFalse();
    }

    @Test
    void aHashOfAnotherAlgorithmNeedsRehashAndStillMatches() {
        PasswordHash noop = new PasswordHash("{noop}secret");

        assertThat(delegatingPasswordHasherAdapter.needsRehash(noop)).isTrue();
        assertThat(delegatingPasswordHasherAdapter.matches("secret", noop)).isTrue();
        assertThat(delegatingPasswordHasherAdapter.matches("other", noop)).isFalse();
    }
}
