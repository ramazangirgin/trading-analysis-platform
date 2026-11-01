package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PermissionTest {

    @Test
    void keysAreUnique() {
        assertThat(Arrays.stream(Permission.values()).map(Permission::key)).doesNotHaveDuplicates();
    }
}
