package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.error.ApiException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;

class IdentityExceptionToApiExceptionMapperTest {

    private final IdentityExceptionToApiExceptionMapper mapper = new IdentityExceptionToApiExceptionMapperImpl();

    @ParameterizedTest
    @CsvSource({
        "BAD_CREDENTIALS, 401, bad_credentials",
        "WRONG_CURRENT_PASSWORD, 400, wrong_current_password",
        "PASSWORD_UNCHANGED, 400, password_unchanged",
        "WEAK_PASSWORD, 400, weak_password",
        "USER_NOT_FOUND, 404, user_not_found",
        "INVALID_USERNAME, 400, invalid_username",
        "UNKNOWN_ROLE, 400, unknown_role",
        "USERNAME_TAKEN, 409, username_taken",
        "CANNOT_DISABLE_SELF, 409, cannot_disable_self",
        "CANNOT_REMOVE_OWN_ADMIN, 409, cannot_remove_own_admin",
        "LAST_ADMIN, 409, last_admin",
        "CONCURRENT_UPDATE, 409, concurrent_update",
        "UNKNOWN_PERMISSION, 500, unknown_permission"
    })
    void mapsEveryErrorCodeToItsStatusAndErrorCode(IdentityError error, int status, String errorCode) {
        ApiException mapped = mapper.map(new IdentityException(error, "failed", Map.of("k", "v")));

        assertThat(mapped.status().value()).isEqualTo(status);
        assertThat(mapped.errorCode()).isEqualTo(errorCode);
        assertThat(mapped.getMessage()).isEqualTo("failed");
        assertThat(mapped.params()).containsEntry("k", "v");
    }
}
