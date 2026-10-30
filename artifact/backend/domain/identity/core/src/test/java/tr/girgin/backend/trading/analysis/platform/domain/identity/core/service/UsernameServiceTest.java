package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;

class UsernameServiceTest {

    private final UsernameService service = new UsernameService();

    @ParameterizedTest
    @ValueSource(strings = {"abc", "Alice", "a.b_c-d", "alice@example.com", "user42"})
    void acceptsValidNames(String name) {
        assertThat(service.username(name).value()).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "ab", "has space", "tab\tname", "ünal", "İlhan", "semi;colon"})
    void rejectsInvalidNames(String name) {
        assertThatThrownBy(() -> service.username(name))
                .isInstanceOfSatisfying(
                        IdentityException.class, e -> assertThat(e.error()).isEqualTo(IdentityError.INVALID_USERNAME));
    }

    @ParameterizedTest
    @ValueSource(ints = {64, 65})
    void limitsTheLengthTo64(int length) {
        String name = "a".repeat(length);

        if (length == 64) {
            assertThat(service.username(name).value()).hasSize(64);
        } else {
            assertThatThrownBy(() -> service.username(name)).isInstanceOf(IdentityException.class);
        }
    }
}
