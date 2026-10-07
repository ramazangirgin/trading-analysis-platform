package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

import java.util.Map;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityError;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;

/** What a role may do. The key is what the database stores. */
public enum Permission {
    ANALYSIS_RUN("analysis:run"),
    ANALYSIS_READ("analysis:read"),
    ANALYSIS_READ_ALL("analysis:read:all"),
    ANALYSIS_DELETE("analysis:delete"),
    PRESET_READ("preset:read"),
    PRESET_MANAGE("preset:manage"),
    PRESET_READ_ALL("preset:read:all"),
    SETTINGS_READ("settings:read"),
    SETTINGS_KEYS_WRITE("settings:keys:write"),
    USER_READ("user:read"),
    USER_MANAGE("user:manage"),
    ROLE_MANAGE("role:manage"),
    AUDIT_READ("audit:read");

    private final String key;

    Permission(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Permission fromKey(String key) {
        for (Permission permission : values()) {
            if (permission.key.equals(key)) {
                return permission;
            }
        }
        throw new IdentityException(
                IdentityError.UNKNOWN_PERMISSION,
                "Unknown permission: " + key,
                Map.of("permission", String.valueOf(key)));
    }
}
