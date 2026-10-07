package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.password;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;

class DelegatingPasswordHasherAdapterTest {

    private final DelegatingPasswordHasherAdapter hasher = new DelegatingPasswordHasherAdapter();

    @Test
    void hashStartsWithBcryptIdAndDiffersFromThePassword() {
        PasswordHash hash = hasher.hash("correct horse");

        assertThat(hash.value()).startsWith("{bcrypt}").isNotEqualTo("correct horse");
    }

    @Test
    void matchesTheRightPasswordOnly() {
        PasswordHash hash = hasher.hash("correct horse");

        assertThat(hasher.matches("correct horse", hash)).isTrue();
        assertThat(hasher.matches("wrong horse", hash)).isFalse();
    }

    @Test
    void twoHashesOfTheSamePasswordDiffer() {
        assertThat(hasher.hash("same")).isNotEqualTo(hasher.hash("same"));
    }

    @Test
    void aFreshHashNeedsNoRehash() {
        assertThat(hasher.needsRehash(hasher.hash("password"))).isFalse();
    }

    @Test
    void aHashOfAnotherAlgorithmNeedsRehashAndStillMatches() {
        PasswordHash noop = new PasswordHash("{noop}secret");

        assertThat(hasher.needsRehash(noop)).isTrue();
        assertThat(hasher.matches("secret", noop)).isTrue();
        assertThat(hasher.matches("other", noop)).isFalse();
    }
}
