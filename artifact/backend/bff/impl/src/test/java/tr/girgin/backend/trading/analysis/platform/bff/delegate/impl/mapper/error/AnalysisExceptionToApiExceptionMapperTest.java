package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.error.ApiException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;

class AnalysisExceptionToApiExceptionMapperTest {

    private final AnalysisExceptionToApiExceptionMapper mapper = new AnalysisExceptionToApiExceptionMapperImpl();

    @Test
    void mapsAConcurrentUpdateToConflict() {
        ApiException mapped =
                mapper.map(new AnalysisException(AnalysisError.CONCURRENT_UPDATE, "changed", Map.of("id", "a1")));

        assertThat(mapped.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mapped.errorCode()).isEqualTo("concurrent_update");
        assertThat(mapped.params()).containsEntry("id", "a1");
    }

    @Test
    void mapsAMissingSharePermissionToForbiddenWithAnAnalysisPrefix() {
        ApiException mapped = mapper.map(new AnalysisException(AnalysisError.SHARE_FORBIDDEN, "no", Map.of()));

        assertThat(mapped.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(mapped.errorCode()).isEqualTo("analysis_share_forbidden");
    }

    @Test
    void mapsAnAnalysisThatCannotBeSharedToConflictWithItsStatus() {
        ApiException mapped =
                mapper.map(new AnalysisException(AnalysisError.NOT_SHAREABLE, "running", Map.of("status", "RUNNING")));

        assertThat(mapped.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mapped.errorCode()).isEqualTo("analysis_not_shareable");
        assertThat(mapped.params()).containsEntry("status", "RUNNING");
    }
}
