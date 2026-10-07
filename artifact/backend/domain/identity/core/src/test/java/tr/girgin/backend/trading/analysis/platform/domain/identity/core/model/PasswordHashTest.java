package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordHashTest {

    @Test
    void toStringHidesTheValue() {
        PasswordHash hash = new PasswordHash("{bcrypt}$2a$10$secret");

        assertThat(hash.toString()).isEqualTo("PasswordHash[***]").doesNotContain("secret");
        assertThat(hash.value()).isEqualTo("{bcrypt}$2a$10$secret");
    }
}
