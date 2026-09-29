package tr.girgin.backend.trading.analysis.platform.domain.settings.core.model;

import java.util.Objects;

/** Whether a secret is set and where from. The value never leaves the backend: only its last characters. */
public record SecretStatus(String name, SecretSource source, String maskedValue) {

    public SecretStatus {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(maskedValue, "maskedValue");
    }
}
