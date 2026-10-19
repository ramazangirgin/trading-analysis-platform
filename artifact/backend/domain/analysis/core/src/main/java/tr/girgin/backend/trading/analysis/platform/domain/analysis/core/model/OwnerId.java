package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.util.Objects;

/** The ID of the user an analysis belongs to. */
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
