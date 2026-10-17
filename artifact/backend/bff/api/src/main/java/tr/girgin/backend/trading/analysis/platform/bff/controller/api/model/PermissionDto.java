package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** What a role may do, on the wire as its key (e.g. {@code analysis:read:all}). */
public enum PermissionDto {
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

    PermissionDto(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }
}
