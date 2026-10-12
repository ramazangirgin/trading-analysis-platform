package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

import java.util.Objects;
import java.util.UUID;

public record RoleId(String value) {

    /** The built-in ADMIN role, seeded with this ID by the identity migration {@code V3__seed_admin_role.sql}. */
    public static final RoleId ADMIN = new RoleId("ADMIN");

    public RoleId {
        Objects.requireNonNull(value, "value");
    }

    public static RoleId newId() {
        return new RoleId(UUID.randomUUID().toString());
    }
}
