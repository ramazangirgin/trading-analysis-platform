package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.util.Map;
import java.util.Objects;

public class AnalysisException extends RuntimeException {

    private final AnalysisError error;
    private final Map<String, Object> params;

    public AnalysisException(AnalysisError error, String message, Map<String, Object> params) {
        super(message);
        this.error = Objects.requireNonNull(error, "error");
        this.params = Map.copyOf(params);
    }

    public AnalysisError error() {
        return error;
    }

    public Map<String, Object> params() {
        return params;
    }
}
