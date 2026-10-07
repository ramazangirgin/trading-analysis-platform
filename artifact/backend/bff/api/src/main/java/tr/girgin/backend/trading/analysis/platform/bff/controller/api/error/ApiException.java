package tr.girgin.backend.trading.analysis.platform.bff.controller.api.error;

import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;

/** Thrown by delegates to answer with an error code the frontend translates. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final Map<String, Object> params;

    public ApiException(HttpStatus status, String errorCode, String message, Map<String, Object> params) {
        super(message);
        this.status = Objects.requireNonNull(status, "status");
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.params = Map.copyOf(params);
    }

    public HttpStatus status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }

    public Map<String, Object> params() {
        return params;
    }
}
