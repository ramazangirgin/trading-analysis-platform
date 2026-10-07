package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;

class PermissionTest {

    @Test
    void fromKeyRoundTripsEveryConstant() {
        for (Permission permission : Permission.values()) {
            assertThat(Permission.fromKey(permission.key())).isEqualTo(permission);
        }
    }

    @Test
    void keysAreUnique() {
        assertThat(Arrays.stream(Permission.values()).map(Permission::key)).doesNotHaveDuplicates();
    }

    @Test
    void unknownKeyThrows() {
        assertThatThrownBy(() -> Permission.fromKey("nope:nothing"))
                .isInstanceOfSatisfying(IdentityException.class, e -> {
                    assertThat(e.error()).isEqualTo(IdentityError.UNKNOWN_PERMISSION);
                    assertThat(e.params()).containsEntry("permission", "nope:nothing");
                });
    }
}
