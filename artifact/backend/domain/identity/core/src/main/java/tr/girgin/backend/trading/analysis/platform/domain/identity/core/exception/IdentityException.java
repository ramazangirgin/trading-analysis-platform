package tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception;

import java.util.Map;
import java.util.Objects;

public class IdentityException extends RuntimeException {

    private final IdentityError error;
    private final Map<String, Object> params;

    public IdentityException(IdentityError error, String message, Map<String, Object> params) {
        super(message);
        this.error = Objects.requireNonNull(error, "error");
        this.params = Map.copyOf(params);
    }

    public IdentityError error() {
        return error;
    }

    public Map<String, Object> params() {
        return params;
    }
}
