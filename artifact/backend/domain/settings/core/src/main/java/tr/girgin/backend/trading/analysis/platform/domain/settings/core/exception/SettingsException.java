package tr.girgin.backend.trading.analysis.platform.domain.settings.core.exception;

import java.util.Map;
import java.util.Objects;

public class SettingsException extends RuntimeException {

    private final SettingsError error;
    private final Map<String, Object> params;

    public SettingsException(SettingsError error, String message, Map<String, Object> params) {
        super(message);
        this.error = Objects.requireNonNull(error, "error");
        this.params = Map.copyOf(params);
    }

    public SettingsError error() {
        return error;
    }

    public Map<String, Object> params() {
        return params;
    }
}
