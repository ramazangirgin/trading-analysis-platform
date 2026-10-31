package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisError;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.exception.AnalysisException;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;

/** An analysis ID is also the run directory name, so its format is strict. */
@Service
class AnalysisIdService {

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final int RANDOM_BYTES = 6;

    private final SecureRandom random = new SecureRandom();

    AnalysisId newId() {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return new AnalysisId("r_" + HexFormat.of().formatHex(bytes));
    }

    boolean isValid(String value) {
        return FORMAT.matcher(value).matches();
    }

    // An invalid ID names no analysis, so it is answered as not found, before any port is called.
    void requireValid(AnalysisId id) {
        if (!isValid(id.value())) {
            throw new AnalysisException(
                    AnalysisError.NOT_FOUND, "Analysis not found: " + id.value(), Map.of("id", id.value()));
        }
    }
}
