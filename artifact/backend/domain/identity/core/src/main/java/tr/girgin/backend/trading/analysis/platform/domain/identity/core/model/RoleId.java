package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

import java.util.Objects;
import java.util.UUID;

public record RoleId(String value) {

    public RoleId {
        Objects.requireNonNull(value, "value");
    }

    public static RoleId newId() {
        return new RoleId(UUID.randomUUID().toString());
    }
}
