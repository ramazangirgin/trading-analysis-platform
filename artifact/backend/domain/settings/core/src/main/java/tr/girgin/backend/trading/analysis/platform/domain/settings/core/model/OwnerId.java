package tr.girgin.backend.trading.analysis.platform.domain.settings.core.model;

import java.util.Objects;

/** The ID of the user a preset belongs to. */
public record OwnerId(String value) {

    public OwnerId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Owner id must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
