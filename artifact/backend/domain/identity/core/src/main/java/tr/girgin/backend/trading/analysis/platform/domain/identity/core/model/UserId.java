package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

import java.util.Objects;
import java.util.UUID;

public record UserId(String value) {

    public UserId {
        Objects.requireNonNull(value, "value");
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID().toString());
    }
}
